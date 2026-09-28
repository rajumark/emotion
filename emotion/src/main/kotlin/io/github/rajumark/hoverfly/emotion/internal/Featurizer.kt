package io.github.rajumark.hoverfly.emotion.internal

import java.text.Normalizer
import java.util.Locale

/**
 * Turns a message into model inputs. Must produce exactly the ids of the reference implementation
 * (emotion/features.py); ParityTest checks it against the vectors in src/test/resources/testvectors.tsv.
 *
 * Python-semantics notes: strings are walked by code point (not UTF-16 unit), `\w` means a letter or number of any
 * script or "_", and whitespace follows Python's str.isspace(). Case is kept for the tokens (emotion lives in
 * "WHY?!"); the lexical features use the lowercased text.
 */
internal class Featurizer(private val sp: SentencePiece) {

    /** Model inputs without padding: at most [MAX_TOKENS] token ids and [MAX_GRAMS] n-gram ids. */
    class Features(val tokIds: IntArray, val gramIds: IntArray)

    fun featurize(text: String): Features {
        val norm = normalize(text)
        val toks = sp.encode(norm)
        return Features(if (toks.size > MAX_TOKENS) toks.copyOf(MAX_TOKENS) else toks, gramIds(norm))
    }

    companion object {
        const val MAX_TOKENS = 64
        const val MAX_GRAMS = 160
        const val N_BUCKETS = 1 shl 15

        /** String.codePoints() needs API 24; this works on every API level. */
        fun codePoints(s: String): IntArray {
            val out = IntArray(s.codePointCount(0, s.length))
            var i = 0
            var k = 0
            while (i < s.length) {
                val cp = s.codePointAt(i)
                out[k++] = cp
                i += Character.charCount(cp)
            }
            return out
        }

        fun isPySpace(cp: Int): Boolean =
            Character.isWhitespace(cp) || Character.isSpaceChar(cp) || cp == 0x85

        /** Python's `\w`: a letter or number of any script, or "_". */
        fun isPyWord(cp: Int): Boolean {
            if (cp == '_'.code) return true
            return when (Character.getType(cp).toByte()) {
                Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER, Character.TITLECASE_LETTER,
                Character.MODIFIER_LETTER, Character.OTHER_LETTER,
                Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER, Character.OTHER_NUMBER -> true
                else -> false
            }
        }

        /** NFKC, URLs -> " @url ", @handles -> "@user", a character repeated 4+ times -> 3, whitespace collapsed.
         *  Case is kept. */
        fun normalize(text: String): String {
            var t = Normalizer.normalize(text, Normalizer.Form.NFKC)
            t = replaceMentions(replaceUrls(t))
            t = collapseRepeats(t)
            val sb = StringBuilder(t.length)
            var pendingSpace = false
            var i = 0
            while (i < t.length) {
                val cp = t.codePointAt(i)
                if (isPySpace(cp)) pendingSpace = true
                else {
                    if (pendingSpace && sb.isNotEmpty()) sb.append(' ')
                    pendingSpace = false
                    sb.appendCodePoint(cp)
                }
                i += Character.charCount(cp)
            }
            return sb.toString()
        }

        /** Python re.sub(r"(https?://|www\.)\S+", " @url ", t). */
        fun replaceUrls(t: String): String {
            val sb = StringBuilder(t.length)
            var i = 0
            while (i < t.length) {
                val prefix = when {
                    t.startsWith("https://", i) -> 8
                    t.startsWith("http://", i) -> 7
                    t.startsWith("www.", i) -> 4
                    else -> 0
                }
                val end = if (prefix > 0) nonSpaceRunEnd(t, i + prefix) else i
                if (prefix > 0 && end > i + prefix) {
                    sb.append(" @url ")
                    i = end
                } else {
                    val cp = t.codePointAt(i)
                    sb.appendCodePoint(cp)
                    i += Character.charCount(cp)
                }
            }
            return sb.toString()
        }

        /** Python re.sub(r"@\w+", "@user", t). */
        fun replaceMentions(t: String): String {
            val sb = StringBuilder(t.length)
            var i = 0
            while (i < t.length) {
                if (t[i] == '@') {
                    var j = i + 1
                    while (j < t.length) {
                        val cp = t.codePointAt(j)
                        if (!isPyWord(cp)) break
                        j += Character.charCount(cp)
                    }
                    if (j > i + 1) { sb.append("@user"); i = j; continue }
                }
                val cp = t.codePointAt(i)
                sb.appendCodePoint(cp)
                i += Character.charCount(cp)
            }
            return sb.toString()
        }

        /** Python re.sub(r"(.)\1{3,}", r"\1\1\1", t): runs of 4+ of one character (not "\n") become 3. */
        fun collapseRepeats(t: String): String {
            val cps = codePoints(t)
            val sb = StringBuilder(t.length)
            var k = 0
            while (k < cps.size) {
                val cp = cps[k]
                var j = k + 1
                while (j < cps.size && cps[j] == cp) j++
                val run = j - k
                val keep = if (cp != '\n'.code && run >= 4) 3 else run
                repeat(keep) { sb.appendCodePoint(cp) }
                k = j
            }
            return sb.toString()
        }

        private fun nonSpaceRunEnd(t: String, from: Int): Int {
            var j = from
            while (j < t.length) {
                val cp = t.codePointAt(j)
                if (isPySpace(cp)) break
                j += Character.charCount(cp)
            }
            return j
        }

        fun fnv1a(s: String): Long {
            var h = 0x811C9DC5L
            for (b in s.toByteArray(Charsets.UTF_8)) {
                h = h xor (b.toLong() and 0xFF)
                h = (h * 0x01000193L) and 0xFFFFFFFFL
            }
            return h
        }

        private fun cpString(cps: IntArray, from: Int, to: Int): String {
            val sb = StringBuilder()
            for (k in from until to) sb.appendCodePoint(cps[k])
            return sb.toString()
        }

        private fun isAsciiWordChar(cp: Int) = cp in 'a'.code..'z'.code || cp in '0'.code..'9'.code || cp == '\''.code

        /** "w:" words ([a-z0-9']+), "b:" bigrams, char 3/4-grams of " " + low + " " (not all-space), "e:" for each
         *  non-ASCII character, "!" and "?". */
        fun lexicalFeatures(norm: String): List<String> {
            val low = norm.lowercase(Locale.ROOT)
            val feats = ArrayList<String>()
            val cps = codePoints(low)
            val words = ArrayList<String>()
            var start = -1
            for (k in 0..cps.size) {
                val w = k < cps.size && isAsciiWordChar(cps[k])
                if (w && start < 0) start = k
                if (!w && start >= 0) { words.add(cpString(cps, start, k)); start = -1 }
            }
            for (w in words) feats.add("w:$w")
            for (k in 0 until words.size - 1) feats.add("b:${words[k]} ${words[k + 1]}")
            val padded = intArrayOf(' '.code) + cps + intArrayOf(' '.code)
            for (n in 3..4) {
                for (k in 0..padded.size - n) {
                    var allSpace = true
                    for (j in k until k + n) if (!isPySpace(padded[j])) { allSpace = false; break }
                    if (!allSpace) feats.add(cpString(padded, k, k + n))
                }
            }
            for (cp in cps) if (cp >= 128 || cp == '!'.code || cp == '?'.code) feats.add("e:" + String(Character.toChars(cp)))
            return feats
        }

        fun gramIds(norm: String): IntArray {
            val ids = LinkedHashSet<Int>()
            for (f in lexicalFeatures(norm)) {
                ids.add((fnv1a(f) % (N_BUCKETS - 1) + 1).toInt())
                if (ids.size >= MAX_GRAMS) break
            }
            return ids.toIntArray()
        }
    }
}
