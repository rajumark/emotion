package io.github.rajumark.hoverfly.emotion

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlin.time.TimeSource
import kotlin.test.assertFailsWith

class EmotionTest {
    private val emotion = ParityTest.testEmotion()

    @Test
    fun thankfulGoodNews() {
        val r = emotion.detect("Finally got the job!! Thank you so much for helping me 🙏")
        println(r)
        assertEquals(Mood.POSITIVE, r.mood)
        assertTrue(r.emotions.any { it.label == Label.GRATITUDE }, r.toString())
        assertTrue(r.all.first { it.label == Label.JOY }.score > r.all.first { it.label == Label.SADNESS }.score, r.toString())
    }

    @Test
    fun annoyedCustomer() {
        val r = emotion.detect("Why is my order STILL not here? This is the third time I'm asking")
        println(r)
        assertEquals(Mood.NEGATIVE, r.mood)
        assertTrue(r.emotions.any { it.label == Label.ANNOYANCE || it.label == Label.ANGER }, r.toString())
    }

    @Test
    fun plainMessagesAreMostlyNeutral() {
        val plain = listOf(
            "The meeting moved to 3 pm", "I'll call you tomorrow", "The train is at 6", "send me the report by friday",
            "the dentist is at 4:30", "ok", "the doctor's appointment is next week", "I'm going to the gym",
        )
        val neutral = plain.map { emotion.detect(it) }.onEach { println(it) }.count { it.top == Label.NEUTRAL && it.mood == Mood.NEUTRAL }
        assertTrue(neutral >= plain.size - 2, "$neutral of ${plain.size} plain messages neutral")
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

    @Test
    fun closedInstanceThrows() {
        val e = Emotion()
        e.close()
        assertFailsWith<IllegalStateException> { e.detect("hello") }
    }

    @Test
    fun latency() {
        val texts = ParityTest.vectors().map { it.input }.filter { it.length in 5..200 }
        repeat(300) { emotion.detect(texts[it % texts.size]) }
        val n = 2000
        val t0 = TimeSource.Monotonic.markNow()
        repeat(n) { emotion.detect(texts[it % texts.size]) }
        val ms = t0.elapsedNow().inWholeNanoseconds / 1e6 / n
        println("latency: ${fmt(ms, 3)} ms per message")
        assertTrue(ms < 200, "too slow: $ms ms") // generous: Kotlin/Native test binaries are unoptimized debug builds
        val l0 = TimeSource.Monotonic.markNow()
        Emotion().close()
        println("load: ${l0.elapsedNow().inWholeMilliseconds} ms")
    }
}
