package io.github.rajumark.hoverfly.emotion.sample

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.rajumark.hoverfly.emotion.Emotion
import io.github.rajumark.hoverfly.emotion.Mood
import io.github.rajumark.hoverfly.emotion.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { EmotionTheme { EmotionScreen() } }
    }
}

private val EXAMPLES = listOf(
    "Finally got the job!! Thank you so much for helping me 🙏",
    "Why is my order STILL not here? This is the third time I'm asking",
    "I miss my grandma so much today",
    "Wait, you're moving to Canada??",
    "Exam tomorrow and I remember nothing 😰",
    "I'll call you after lunch",
)

private class Timed(val result: Result, val micros: Long)

private fun moodFace(m: Mood) = when (m) {
    Mood.POSITIVE -> "😊"
    Mood.NEGATIVE -> "😟"
    Mood.AMBIGUOUS -> "😮"
    Mood.NEUTRAL -> "😐"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EmotionScreen() {
    val context = LocalContext.current.applicationContext
    val emotion by produceState<Emotion?>(null) {
        value = withContext(Dispatchers.Default) { Emotion(context) }
        awaitDispose { value?.close() }
    }
    var input by remember { mutableStateOf(EXAMPLES[0]) }
    val timed by produceState<Timed?>(null, emotion, input) {
        val e = emotion ?: return@produceState
        value = withContext(Dispatchers.Default) {
            val t0 = System.nanoTime()
            val r = e.detect(input)
            Timed(r, (System.nanoTime() - t0) / 1000)
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Emotion") }) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Message") },
                minLines = 2,
                trailingIcon = {
                    if (input.isNotEmpty()) IconButton(onClick = { input = "" }) { Icon(Icons.Filled.Clear, "Clear") }
                },
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

@Composable
private fun ScoreRow(name: String, score: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box(Modifier.width(110.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(score.coerceIn(0.02f, 1f)).background(MaterialTheme.colorScheme.primary))
        }
        Text("%.0f%%".format(score * 100), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(40.dp))
    }
}

@Composable
fun EmotionTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}
