package io.github.rajumark.hoverfly.emotion.sample

import io.github.rajumark.hoverfly.emotion.Emotion
import io.github.rajumark.hoverfly.emotion.Mood
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLTextAreaElement
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/** "Kotlin/JS" or "Kotlin/Wasm". */
expect val runtime: String

private val examples = listOf(
    "Finally got the job!! Thank you so much for helping me 🙏",
    "Why is my order STILL not here? This is the third time I'm asking",
    "I miss my grandma so much today",
    "Wait, you're moving to Canada??",
    "Exam tomorrow and I remember nothing 😰",
    "I'll call you after lunch",
)

private fun face(m: Mood) = when (m) { Mood.POSITIVE -> "😊"; Mood.NEGATIVE -> "😟"; Mood.AMBIGUOUS -> "😮"; Mood.NEUTRAL -> "😐" }

private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

private fun row(name: String, score: Float) =
    "<div class=\"bar\"><span class=\"lbl\">${esc(name)}</span><div class=\"track\"><div class=\"fill\" style=\"width:${(score * 100).roundToInt()}%\"></div></div>" +
        "<span class=\"pct\">${(score * 100).roundToInt()}%</span></div>"

fun main() {
    fun el(id: String) = document.getElementById(id) as HTMLElement
    val input = document.getElementById("text") as HTMLTextAreaElement
    el("platform").textContent = "Kotlin Multiplatform · $runtime · io.github.rajumark:emotion:2.0.0"

    val t0 = TimeSource.Monotonic.markNow()
    val emotion = Emotion()
    el("load").textContent = "Model loaded in ${t0.elapsedNow().inWholeMilliseconds} ms"

    fun render() {
        val mark = TimeSource.Monotonic.markNow()
        val r = emotion.detect(input.value)
        val micros = mark.elapsedNow().inWholeMicroseconds
        el("mood").textContent = face(r.mood) + "  " + r.mood.name.lowercase().replaceFirstChar { it.uppercase() }
        el("emotions").innerHTML = r.emotions.take(5).joinToString("") { row(it.label.name.lowercase(), it.score) }
        el("basic").innerHTML = r.basic.take(3).joinToString("") { row(it.emotion.name.lowercase(), it.score) }
        el("timing").textContent = "$micros µs"
    }

    el("examples").innerHTML = examples.joinToString("") { "<button class=\"chip\">${esc(it)}</button>" }
    val list = el("examples").querySelectorAll("button")
    for (i in 0 until list.length) {
        val b = list.item(i) as HTMLElement
        b.onclick = { input.value = b.textContent ?: ""; render(); null }
    }
    input.oninput = { render(); null }
    input.value = examples[0]
    render()
}
