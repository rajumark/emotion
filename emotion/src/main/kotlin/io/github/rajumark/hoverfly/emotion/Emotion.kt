package io.github.rajumark.hoverfly.emotion

import android.content.Context
import io.github.rajumark.hoverfly.emotion.internal.Featurizer
import io.github.rajumark.hoverfly.emotion.internal.Network
import io.github.rajumark.hoverfly.emotion.internal.SentencePiece
import java.io.Closeable
import java.io.InputStream

/**
 * On-device emotion detection for English: finds the emotions in a message, out of the 27 GoEmotions emotions plus
 * neutral, and sums them up as a mood.
 *
 * ```
 * Emotion(context).use { emotion ->
 *     val r = emotion.detect("Finally got the job!! Thank you so much for helping me 🙏")
 *     r.emotions   // [joy 0.83, gratitude 0.78, excitement 0.41]
 *     r.mood       // POSITIVE
 * }
 * ```
 *
 * A message can carry several emotions at once. Everything runs locally: the model ships inside the library, there
 * is no network, no permission and no dependency. Creating an instance reads the model (tens of ms), so create it off
 * the main thread and keep it around; [detect] takes a few milliseconds and is safe to call from several threads.
 */
public class Emotion internal constructor(open: (String) -> InputStream) : Closeable {

    /** Loads the model bundled in the library's assets. */
    public constructor(context: Context) : this({ name -> context.assets.open("$ASSET_DIR/$name") })

    private var network: Network? = open("emotion.bin").use { Network(it) }
    private val featurizer = Featurizer(open("spm_pieces.tsv").use { SentencePiece(it) })

    /** One decision threshold per label (same order as [Label]), tuned for the best F1 on GoEmotions' validation set. */
    private val thresholds: FloatArray = open("thresholds.txt").bufferedReader(Charsets.UTF_8).readLines()
        .filter { it.isNotBlank() }.map { it.trim().toFloat() }.toFloatArray()

    init {
        check(thresholds.size == Label.entries.size) { "thresholds.txt must have ${Label.entries.size} lines" }
        // The first calls run interpreted; pay that here (off the UI thread) instead of on the first message.
        repeat(WARM_UP) { detect("warm up $it: thank you so much, this made my day!") }
    }

    /**
     * The emotions in [text]. [Result.emotions] holds every label whose score passes its threshold, best first, and
     * always at least the single most likely label. A blank text is [Label.NEUTRAL].
     */
    public fun detect(text: String): Result {
        val net = checkNotNull(network) { "Emotion is closed" }
        val f = featurizer.featurize(text)
        val out = net.probs(f.tokIds, f.gramIds)
        val all = Label.entries.map { Score(it, out.fine[it.ordinal]) }.sortedByDescending { it.score }
        val passed = all.filter { it.score >= thresholds[it.label.ordinal] }
        val emotions = passed.ifEmpty { listOf(all[0]) }
        val basic = BasicEmotion.entries.map { BasicScore(it, out.ekman[it.ordinal]) }.sortedByDescending { it.score }
        return Result(emotions, all, basic, emotions[0].label.mood)
    }

    /** Releases the model. The instance cannot be used afterwards. */
    override fun close() {
        network = null
    }

    internal fun rawProbs(text: String): Network.Output {
        val net = checkNotNull(network) { "Emotion is closed" }
        val f = featurizer.featurize(text)
        return net.probs(f.tokIds, f.gramIds)
    }

    internal companion object {
        const val ASSET_DIR = "emotion"
        const val WARM_UP = 20
    }
}

/**
 * What [Emotion.detect] found.
 *
 * @property emotions the labels that pass their thresholds, best first (at least one).
 * @property all all 28 labels with their scores, best first.
 * @property basic the 7 basic emotions (Ekman's six + neutral) with their scores, best first.
 * @property mood the overall mood, from the top emotion.
 */
public class Result(
    public val emotions: List<Score>,
    public val all: List<Score>,
    public val basic: List<BasicScore>,
    public val mood: Mood,
) {
    /** The single most likely emotion. */
    public val top: Label get() = emotions[0].label

    override fun toString(): String = "Result(mood=$mood, emotions=$emotions)"
}

/** One emotion label and its score, 0..1. */
public data class Score(val label: Label, val score: Float) {
    override fun toString(): String = "${label.name.lowercase()} %.2f".format(score)
}

/** One basic emotion and its score, 0..1. */
public data class BasicScore(val emotion: BasicEmotion, val score: Float) {
    override fun toString(): String = "${emotion.name.lowercase()} %.2f".format(score)
}

/** The overall feeling of a message. */
public enum class Mood { POSITIVE, NEGATIVE, AMBIGUOUS, NEUTRAL }

/** Ekman's six basic emotions plus neutral. */
public enum class BasicEmotion { ANGER, DISGUST, FEAR, JOY, SADNESS, SURPRISE, NEUTRAL }

/** The 27 GoEmotions emotions plus neutral, with the mood each one belongs to. */
public enum class Label(public val mood: Mood) {
    ADMIRATION(Mood.POSITIVE), AMUSEMENT(Mood.POSITIVE), ANGER(Mood.NEGATIVE), ANNOYANCE(Mood.NEGATIVE),
    APPROVAL(Mood.POSITIVE), CARING(Mood.POSITIVE), CONFUSION(Mood.AMBIGUOUS), CURIOSITY(Mood.AMBIGUOUS),
    DESIRE(Mood.POSITIVE), DISAPPOINTMENT(Mood.NEGATIVE), DISAPPROVAL(Mood.NEGATIVE), DISGUST(Mood.NEGATIVE),
    EMBARRASSMENT(Mood.NEGATIVE), EXCITEMENT(Mood.POSITIVE), FEAR(Mood.NEGATIVE), GRATITUDE(Mood.POSITIVE),
    GRIEF(Mood.NEGATIVE), JOY(Mood.POSITIVE), LOVE(Mood.POSITIVE), NERVOUSNESS(Mood.NEGATIVE), OPTIMISM(Mood.POSITIVE),
    PRIDE(Mood.POSITIVE), REALIZATION(Mood.AMBIGUOUS), RELIEF(Mood.POSITIVE), REMORSE(Mood.NEGATIVE),
    SADNESS(Mood.NEGATIVE), SURPRISE(Mood.AMBIGUOUS), NEUTRAL(Mood.NEUTRAL),
}
