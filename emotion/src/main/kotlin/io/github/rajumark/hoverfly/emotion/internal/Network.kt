package io.github.rajumark.hoverfly.emotion.internal

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * The Emotion network in plain Kotlin:
 *
 *   lexical:  mean of hashed n-gram embeddings -> LayerNorm
 *   semantic: token + position embeddings -> LayerNorm -> N transformer layers (4 heads, post-LN) -> attention pool
 *   head:     [lexical, semantic] -> Linear -> GELU -> { Linear -> 28 GoEmotions sigmoids, Linear -> 7 Ekman sigmoids }
 *
 * Only real (non-padding) token positions are computed: padded keys get a -1e4 score in the reference model, whose
 * softmax weight underflows to exactly 0, so the result is the same. Stateless after loading: one instance can serve
 * several threads.
 */
internal class Network(bin: InputStream) {

    /** Row-wise symmetric int8 matrix: w[r][c] = scale[r] * q[r * cols + c]. */
    class Q8(val rows: Int, val cols: Int, val scale: FloatArray, val q: ByteArray) {
        /** Dequantized row-major copy, for the dense layers (float math is faster than int8 on ART). */
        fun dense(): Dense = Dense(rows, cols, FloatArray(rows * cols) { scale[it / cols] * q[it] })
    }

    class Dense(val rows: Int, val cols: Int, val w: FloatArray)

    /** Sigmoid probabilities of the two heads. */
    class Output(val fine: FloatArray, val ekman: FloatArray)

    private class Layer(
        val qkv: Dense, val qkvB: FloatArray,
        val out: Dense, val outB: FloatArray,
        val ln1: Pair<FloatArray, FloatArray>,
        val ff1: Dense, val ff1B: FloatArray,
        val ff2: Dense, val ff2B: FloatArray,
        val ln2: Pair<FloatArray, FloatArray>,
    )

    private val lex: Q8
    private val tok: Q8
    private val pos: Q8
    private val lnLex: Pair<FloatArray, FloatArray>
    private val ln0: Pair<FloatArray, FloatArray>
    private val layers: List<Layer>
    private val pool: Dense
    private val poolB: FloatArray
    private val hidden: Dense
    private val hiddenB: FloatArray
    private val fineW: Dense
    private val fineB: FloatArray
    private val ekmanW: Dense
    private val ekmanB: FloatArray
    private val dSem: Int
    private val dHead: Int

    val maxTokens: Int get() = pos.rows

    init {
        val t = read(bin)
        fun q(n: String) = t[n] as? Q8 ?: error("emotion.bin: missing matrix $n")
        fun f(n: String) = t[n] as? FloatArray ?: error("emotion.bin: missing vector $n")
        fun ln(n: String) = f("$n.weight") to f("$n.bias")
        lex = q("lex.weight"); tok = q("tok.weight"); pos = q("pos.weight")
        lnLex = ln("ln_lex"); ln0 = ln("ln0")
        layers = generateSequence(0) { it + 1 }.takeWhile { t.containsKey("blocks.$it.qkv.weight") }.map { i ->
            val p = "blocks.$i"
            Layer(
                q("$p.qkv.weight").dense(), f("$p.qkv.bias"),
                q("$p.out.weight").dense(), f("$p.out.bias"),
                ln("$p.ln1"),
                q("$p.ff.0.weight").dense(), f("$p.ff.0.bias"),
                q("$p.ff.2.weight").dense(), f("$p.ff.2.bias"),
                ln("$p.ln2"),
            )
        }.toList()
        check(layers.isNotEmpty()) { "emotion.bin: no transformer layers" }
        pool = q("pool.weight").dense(); poolB = f("pool.bias")
        hidden = q("hidden.weight").dense(); hiddenB = f("hidden.bias")
        fineW = q("fine.weight").dense(); fineB = f("fine.bias")
        ekmanW = q("ekman.weight").dense(); ekmanB = f("ekman.bias")
        dSem = tok.cols
        dHead = dSem / HEADS
    }

    fun probs(tokIds: IntArray, gramIds: IntArray): Output {
        // ---- lexical stream: masked mean of n-gram embeddings
        val dLex = lex.cols
        val lexV = FloatArray(dLex)
        var nGram = 0
        for (g in gramIds) if (g > 0) { addRow(lex, g, lexV); nGram++ }
        if (nGram > 0) for (c in 0 until dLex) lexV[c] /= nGram.toFloat()
        layerNorm(lexV, 0, dLex, lnLex)

        // ---- semantic stream. An empty input still attends to position 0 (token 0 = padding).
        val n = maxOf(1, tokIds.size)
        val d = dSem
        val x = FloatArray(n * d)
        for (i in 0 until n) {
            if (i < tokIds.size) addRow(tok, tokIds[i], x, i * d)
            addRow(pos, i, x, i * d)
            layerNorm(x, i * d, d, ln0)
        }
        val qkv = FloatArray(n * 3 * d)
        val att = FloatArray(n * d)
        val s = FloatArray(n)
        val tmpAll = FloatArray(n * d)
        val dff = layers[0].ff1.rows
        val hidAll = FloatArray(n * dff)
        val inv = 1f / sqrt(dHead.toFloat())
        for (l in layers) {
            linearAll(l.qkv, l.qkvB, x, d, n, qkv, 3 * d)
            att.fill(0f)
            for (h in 0 until HEADS) {
                val ho = h * dHead
                for (i in 0 until n) {
                    val qo = i * 3 * d + ho
                    for (j in 0 until n) s[j] = dot(qkv, qo, qkv, j * 3 * d + d + ho, dHead) * inv
                    softmax(s, n)
                    val ao = i * d + ho
                    for (j in 0 until n) {
                        val w = s[j]
                        val vo = j * 3 * d + 2 * d + ho
                        for (c in 0 until dHead) att[ao + c] += w * qkv[vo + c]
                    }
                }
            }
            linearAll(l.out, l.outB, att, d, n, tmpAll, d)
            for (i in 0 until n) {
                for (c in 0 until d) x[i * d + c] += tmpAll[i * d + c]
                layerNorm(x, i * d, d, l.ln1)
            }
            linearAll(l.ff1, l.ff1B, x, d, n, hidAll, dff)
            for (k in 0 until n * dff) hidAll[k] = gelu(hidAll[k])
            linearAll(l.ff2, l.ff2B, hidAll, dff, n, tmpAll, d)
            for (i in 0 until n) {
                for (c in 0 until d) x[i * d + c] += tmpAll[i * d + c]
                layerNorm(x, i * d, d, l.ln2)
            }
        }
        // attention pooling
        val w = FloatArray(n)
        val pw = FloatArray(1)
        for (i in 0 until n) { linear(pool, poolB, x, i * d, pw, 0); w[i] = pw[0] }
        softmax(w, n)

        // ---- heads on [lexical, semantic]
        val z = FloatArray(dLex + d)
        lexV.copyInto(z)
        for (i in 0 until n) for (c in 0 until d) z[dLex + c] += w[i] * x[i * d + c]
        val h = FloatArray(hidden.rows)
        linear(hidden, hiddenB, z, 0, h, 0)
        for (c in h.indices) h[c] = gelu(h[c])
        val fine = FloatArray(fineW.rows)
        linear(fineW, fineB, h, 0, fine, 0)
        val ekman = FloatArray(ekmanW.rows)
        linear(ekmanW, ekmanB, h, 0, ekman, 0)
        for (c in fine.indices) fine[c] = sigmoid(fine[c])
        for (c in ekman.indices) ekman[c] = sigmoid(ekman[c])
        return Output(fine, ekman)
    }

    companion object {
        private const val HEADS = 4
        private const val EPS = 1e-5f

        private fun sigmoid(v: Float): Float = (1.0 / (1.0 + exp(-v.toDouble()))).toFloat()

        /** The same linear layer for all n rows of x (row stride xs), weight rows outer so each stays in cache. */
        private fun linearAll(m: Dense, b: FloatArray, x: FloatArray, xs: Int, n: Int, out: FloatArray, os: Int) {
            val cols = m.cols
            for (r in 0 until m.rows) {
                val wo = r * cols
                val br = b[r]
                for (i in 0 until n) out[i * os + r] = br + dot(m.w, wo, x, i * xs, cols)
            }
        }

        /** out[oo + r] = b[r] + sum_c W[r][c] * x[xo + c] */
        private fun linear(m: Dense, b: FloatArray, x: FloatArray, xo: Int, out: FloatArray, oo: Int) {
            for (r in 0 until m.rows) out[oo + r] = b[r] + dot(m.w, r * m.cols, x, xo, m.cols)
        }

        /** sum_c a[ao + c] * b[bo + c], with 4 independent accumulators (ART does not vectorise; this is ~2x). */
        private fun dot(a: FloatArray, ao: Int, b: FloatArray, bo: Int, n: Int): Float {
            var s0 = 0f; var s1 = 0f; var s2 = 0f; var s3 = 0f
            var i = 0
            val n4 = n - 3
            while (i < n4) {
                s0 += a[ao + i] * b[bo + i]
                s1 += a[ao + i + 1] * b[bo + i + 1]
                s2 += a[ao + i + 2] * b[bo + i + 2]
                s3 += a[ao + i + 3] * b[bo + i + 3]
                i += 4
            }
            while (i < n) { s0 += a[ao + i] * b[bo + i]; i++ }
            return (s0 + s1) + (s2 + s3)
        }

        /** out[oo..] += row r of the matrix (an embedding lookup). */
        private fun addRow(m: Q8, r: Int, out: FloatArray, oo: Int = 0) {
            val sc = m.scale[r]
            val base = r * m.cols
            for (c in 0 until m.cols) out[oo + c] += sc * m.q[base + c]
        }

        private fun layerNorm(v: FloatArray, o: Int, n: Int, p: Pair<FloatArray, FloatArray>) {
            var mean = 0f
            for (c in 0 until n) mean += v[o + c]
            mean /= n
            var varc = 0f
            for (c in 0 until n) { val t = v[o + c] - mean; varc += t * t }
            val inv = 1f / sqrt(varc / n + EPS)
            val (g, b) = p
            for (c in 0 until n) v[o + c] = (v[o + c] - mean) * inv * g[c] + b[c]
        }

        private fun softmax(v: FloatArray, n: Int) {
            var mx = Float.NEGATIVE_INFINITY
            for (i in 0 until n) if (v[i] > mx) mx = v[i]
            var sum = 0f
            for (i in 0 until n) { v[i] = exp(v[i] - mx); sum += v[i] }
            for (i in 0 until n) v[i] /= sum
        }

        /** Exact GELU, 0.5 x (1 + erf(x / sqrt 2)), as torch.nn.GELU(). */
        private fun gelu(x: Float): Float = (0.5 * x * (1.0 + erf(x / 1.4142135623730951))).toFloat()

        /** erf via the Numerical Recipes erfc Chebyshev fit, fractional error < 1.2e-7. */
        private fun erf(z: Double): Double {
            val a = kotlin.math.abs(z)
            val t = 1.0 / (1.0 + 0.5 * a)
            val r = t * exp(-a * a - 1.26551223 + t * (1.00002368 + t * (0.37409196 + t * (0.09678418 +
                t * (-0.18628806 + t * (0.27886807 + t * (-1.13520398 + t * (1.48851587 +
                t * (-0.82215223 + t * 0.17087277)))))))))
            return if (z >= 0) 1.0 - r else r - 1.0
        }

        /** Parses emotion.bin: "MOJI" magic, version 1, then named tensors (int8 matrices, fp32 vectors). */
        private fun read(input: InputStream): Map<String, Any> {
            val buf = ByteBuffer.wrap(input.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(4).also { buf.get(it) }
            require(String(magic, Charsets.US_ASCII) == "MOJI") { "not an emotion.bin file" }
            val version = buf.int
            require(version == 1) { "unsupported emotion.bin version $version" }
            val out = HashMap<String, Any>()
            repeat(buf.int) {
                val name = ByteArray(buf.short.toInt() and 0xFFFF).also { buf.get(it) }.toString(Charsets.UTF_8)
                val dtype = buf.get().toInt()
                val dims = IntArray(buf.get().toInt()) { buf.int }
                val size = dims.fold(1) { a, b -> a * b }
                out[name] = when (dtype) {
                    0 -> FloatArray(size).also { buf.asFloatBuffer().get(it); buf.position(buf.position() + 4 * size) }
                    1 -> {
                        val scale = FloatArray(dims[0]).also {
                            buf.asFloatBuffer().get(it); buf.position(buf.position() + 4 * dims[0])
                        }
                        Q8(dims[0], dims[1], scale, ByteArray(size).also { buf.get(it) })
                    }
                    else -> error("emotion.bin: unknown dtype $dtype for $name")
                }
            }
            return out
        }
    }
}
