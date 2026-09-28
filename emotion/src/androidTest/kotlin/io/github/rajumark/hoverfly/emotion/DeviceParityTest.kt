package io.github.rajumark.hoverfly.emotion

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Same check as the JVM ParityTest, on a real device (Android's ICU-backed NFKC, lowercase and character types, and
 * ART's floating point): all probabilities within 0.002 on every vector. Also measures load time and latency.
 */
@RunWith(AndroidJUnit4::class)
class DeviceParityTest {
    @Test
    fun matchesReferenceOnDevice() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val rows = inst.context.assets.open("testvectors.tsv").bufferedReader().readLines().map { it.split('\t') }
        val t0 = System.nanoTime()
        val emotion = Emotion(inst.targetContext)
        val loadMs = (System.nanoTime() - t0) / 1e6
        var maxDiff = 0f
        val texts = ArrayList<String>()
        for (c in rows) {
            val text = unescape(c[0])
            texts.add(text)
            val want = c[3].split(',').map { it.toFloat() } + c[4].split(',').map { it.toFloat() }
            val o = emotion.rawProbs(text)
            val got = o.fine.toList() + o.ekman.toList()
            for (k in want.indices) maxDiff = maxOf(maxDiff, abs(got[k] - want[k]))
        }
        val short = texts.filter { it.length in 5..200 }
        repeat(200) { emotion.detect(short[it % short.size]) }
        val n = 1000
        val s0 = System.nanoTime()
        repeat(n) { emotion.detect(short[it % short.size]) }
        val ms = (System.nanoTime() - s0) / 1e6 / n
        android.util.Log.i("EMOTION_DEVICE", "vectors ${rows.size} maxDiff=$maxDiff load=${"%.0f".format(loadMs)}ms latency=${"%.3f".format(ms)}ms")
        assertTrue("maxDiff $maxDiff", maxDiff < 0.002f)
    }

    /** testvectors.tsv escapes backslash, newline, tab and carriage return in its text column. */
    private fun unescape(s: String): String {
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
}
