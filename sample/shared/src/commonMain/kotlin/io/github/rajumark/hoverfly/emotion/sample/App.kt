package io.github.rajumark.hoverfly.emotion.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.rajumark.hoverfly.emotion.Emotion
import io.github.rajumark.hoverfly.emotion.Mood
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.time.TimeSource
import io.github.rajumark.hoverfly.emotion.Result as EmotionResult

val EXAMPLES = listOf(
    "Finally got the job!! Thank you so much for helping me 🙏",
    "Why is my order STILL not here? This is the third time I'm asking",
    "I miss my grandma so much today",
    "Wait, you're moving to Canada??",
    "Exam tomorrow and I remember nothing 😰",
    "I'll call you after lunch",
)

private class Timed(val result: EmotionResult, val micros: Long)

fun moodFace(m: Mood) = when (m) {
    Mood.POSITIVE -> "😊"
    Mood.NEGATIVE -> "😟"
    Mood.AMBIGUOUS -> "😮"
    Mood.NEUTRAL -> "😐"
}

/** The whole demo: type a message, see its emotions and mood. [platform] is shown so screenshots say where they ran. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun App(platform: String) {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            val emotion by produceState<Emotion?>(null) { value = withContext(Dispatchers.Default) { Emotion() } }
            var input by remember { mutableStateOf(EXAMPLES[0]) }
            val timed by produceState<Timed?>(null, emotion, input) {
                val e = emotion ?: return@produceState
                value = withContext(Dispatchers.Default) {
                    val t0 = TimeSource.Monotonic.markNow()
                    val r = e.detect(input)
                    Timed(r, t0.elapsedNow().inWholeMicroseconds)
                }
            }

            Column(
                Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Emotion", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Kotlin Multiplatform · $platform · io.github.rajumark:emotion:$EMOTION_VERSION",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Message") },
                    minLines = 2,
                )
                Column(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(20.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val t = timed
                    if (emotion == null || t == null) {
                        Box(Modifier.fillMaxWidth().height(48.dp), Alignment.Center) { CircularProgressIndicator() }
                    } else {
                        val r = t.result
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(moodFace(r.mood), style = MaterialTheme.typography.headlineMedium)
                            Text(r.mood.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold)
                        }
                        Text("Emotions", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        r.emotions.take(5).forEach { s -> ScoreRow(s.label.name.lowercase(), s.score) }
                        Text("Basic emotions", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        r.basic.take(3).forEach { s -> ScoreRow(s.emotion.name.lowercase(), s.score) }
                        Text("${t.micros} µs", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("Try", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EXAMPLES.forEach { SuggestionChip(onClick = { input = it }, label = { Text(it, maxLines = 1) }) }
                }
                Text(
                    "English · 28 emotions · runs on this device · no network, no permission",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ScoreRow(name: String, score: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box(Modifier.width(110.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(score.coerceIn(0.02f, 1f)).background(MaterialTheme.colorScheme.primary))
        }
        Text("${(score * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(40.dp))
    }
}

const val EMOTION_VERSION = "2.0.0"
