@file:OptIn(ExperimentalWasmJsInterop::class)

import kotlin.js.ExperimentalWasmJsInterop
import io.github.rajumark.hoverfly.emotion.Emotion

// Website live demo: docs/demo/worker.js calls load() once, then run() per input; run() returns JSON.

private fun q(s: String) = buildString {
    append('"')
    for (c in s) when (c) {
        '"' -> append("\\\""); '\\' -> append("\\\\")
        else -> if (c < ' ') append("\\u").append(c.code.toString(16).padStart(4, '0')) else append(c)
    }
    append('"')
}

private var instance: Emotion? = null

private fun model(): Emotion = instance ?: Emotion().also { instance = it }

/** Loads the bundled model and warms it up. */
@JsExport
fun load() {
    model()
}

/** {mood, emotions: [{label, score}] (passing their thresholds), all: the top 8 of all 28 labels}. */
@JsExport
fun run(input: String, option: String): String {
    val r = model().detect(input)
    fun list(s: List<io.github.rajumark.hoverfly.emotion.Score>) =
        s.joinToString(",", "[", "]") { "{\"label\":${q(it.label.name.lowercase())},\"score\":${it.score}}" }
    return "{\"mood\":${q(r.mood.name.lowercase())},\"emotions\":${list(r.emotions)},\"all\":${list(r.all.take(8))}}"
}

fun main() {}
