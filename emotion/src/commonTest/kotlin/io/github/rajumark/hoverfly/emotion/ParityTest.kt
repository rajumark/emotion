package io.github.rajumark.hoverfly.emotion

import io.github.rajumark.hoverfly.emotion.internal.Featurizer
import io.github.rajumark.hoverfly.emotion.internal.SentencePiece
import io.github.rajumark.hoverfly.emotion.internal.TestData
import io.github.rajumark.hoverfly.emotion.internal.decodeChunks
import io.github.rajumark.hoverfly.emotion.internal.readModelFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.math.abs

/**
 * Checks the Kotlin port against the reference implementation on testvectors.tsv: identical token and n-gram ids,
 * and all 28 + 7 probabilities within 0.002.
 * Runs on every target.
 */
class ParityTest {
    class Vector(val input: String, val tok: List<Int>, val gram: List<Int>, val fine: List<Float>, val ekman: List<Float>)

    @Test
    fun featurizerMatchesReference() {
        val f = Featurizer(SentencePiece(readModelFile("spm_pieces.tsv").decodeToString()))
        var bad = 0
        for (v in vectors()) {
            val got = f.featurize(v.input)
            if (got.tokIds.toList() != v.tok || got.gramIds.toList() != v.gram) {
                bad++
                println("MISMATCH: ${v.input.take(80)}\n  tok  ${got.tokIds.toList().take(20)}\n  want ${v.tok.take(20)}\n" +
                    "  gram ${got.gramIds.toList().take(12)}\n  want ${v.gram.take(12)}")
            }
        }
        println("featurizer: ${vectors().size - bad}/${vectors().size} identical")
        assertEquals(0, bad)
    }

    @Test
    fun modelMatchesReference() {
        val e = testEmotion()
        var maxDiff = 0f
        var topSame = 0
        for (v in vectors()) {
            val o = e.rawProbs(v.input)
            for (k in v.fine.indices) maxDiff = maxOf(maxDiff, abs(o.fine[k] - v.fine[k]))
            for (k in v.ekman.indices) maxDiff = maxOf(maxDiff, abs(o.ekman[k] - v.ekman[k]))
            if (o.fine.indices.maxByOrNull { o.fine[it] } == v.fine.indices.maxByOrNull { v.fine[it] }) topSame++
        }
        println("model: top label $topSame/${vectors().size}, max prob diff $maxDiff")
        assertTrue(maxDiff < 0.002f, "probabilities differ by $maxDiff")
        assertTrue(topSame >= vectors().size - 1, "top differs: $topSame/${vectors().size}")
    }

    companion object {
        private var shared: Emotion? = null

        /** One instance per test run: loading is the slow part on the native and web targets. */
        fun testEmotion(): Emotion = shared ?: Emotion().also { shared = it }


        private fun ints(s: String) = if (s.isEmpty()) emptyList() else s.split(',').map { it.toInt() }
        private fun floats(s: String) = s.split(',').map { it.toFloat() }

        fun unescape(s: String): String {
            val sb = StringBuilder(s.length)
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    when (s[i + 1]) {
                        'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r'); else -> sb.append(s[i + 1])
                    }
                    i += 2
                } else {
                    sb.append(c); i++
                }
            }
            return sb.toString()
        }

        private var cached: List<Vector>? = null
        fun vectors(): List<Vector> = cached ?: decodeChunks(TestData.files.getValue("testvectors.tsv")).decodeToString()
            .lines().filter { it.isNotEmpty() }.map { it.split('\t') }
            .map { Vector(unescape(it[0]), ints(it[1]), ints(it[2]), floats(it[3]), floats(it[4])) }
            .also { cached = it }
    }
}
