package io.github.rajumark.hoverfly.emotion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmotionTest {
    private val emotion = ParityTest.testEmotion()

    @Test
    fun thankfulGoodNews() {
        val r = emotion.detect("Finally got the job!! Thank you so much for helping me 🙏")
        println(r)
        assertEquals(Mood.POSITIVE, r.mood)
        assertTrue(r.toString(), r.emotions.any { it.label == Label.GRATITUDE })
        assertTrue(r.toString(), r.all.first { it.label == Label.JOY }.score > r.all.first { it.label == Label.SADNESS }.score)
    }

    @Test
    fun annoyedCustomer() {
        val r = emotion.detect("Why is my order STILL not here? This is the third time I'm asking")
        println(r)
        assertEquals(Mood.NEGATIVE, r.mood)
        assertTrue(r.toString(), r.emotions.any { it.label == Label.ANNOYANCE || it.label == Label.ANGER })
    }

    @Test
    fun plainMessagesAreMostlyNeutral() {
        val plain = listOf(
            "The meeting moved to 3 pm", "I'll call you tomorrow", "The train is at 6", "send me the report by friday",
            "the dentist is at 4:30", "ok", "the doctor's appointment is next week", "I'm going to the gym",
        )
        val neutral = plain.map { emotion.detect(it) }.onEach { println(it) }.count { it.top == Label.NEUTRAL && it.mood == Mood.NEUTRAL }
        assertTrue("$neutral of ${plain.size} plain messages neutral", neutral >= plain.size - 2)
    }

    @Test
    fun resultShape() {
        val r = emotion.detect("I miss my grandma so much today")
        assertEquals(28, r.all.size)
        assertEquals(7, r.basic.size)
        assertTrue(r.emotions.isNotEmpty())
        assertTrue(r.all.zipWithNext().all { (a, b) -> a.score >= b.score })
        assertTrue(r.basic.zipWithNext().all { (a, b) -> a.score >= b.score })
        assertEquals(r.emotions[0].label, r.all[0].label)
    }

    @Test
    fun blankAndHugeInputs() {
        assertTrue(emotion.detect("").emotions.isNotEmpty())
        assertTrue(emotion.detect("   ").emotions.isNotEmpty())
        assertTrue(emotion.detect("I love this ".repeat(500)).emotions.isNotEmpty())
    }

    @Test(expected = IllegalStateException::class)
    fun closedInstanceThrows() {
        val e = ParityTest.testEmotion()
        e.close()
        e.detect("hello")
    }

    @Test
    fun latency() {
        val texts = ParityTest.vectors().map { it.input }.filter { it.length in 5..200 }
        repeat(300) { emotion.detect(texts[it % texts.size]) }
        val n = 2000
        val t0 = System.nanoTime()
        repeat(n) { emotion.detect(texts[it % texts.size]) }
        val ms = (System.nanoTime() - t0) / 1e6 / n
        println("JVM latency: %.3f ms per message".format(ms))
        assertTrue("too slow: $ms ms", ms < 20.0)
    }
}
