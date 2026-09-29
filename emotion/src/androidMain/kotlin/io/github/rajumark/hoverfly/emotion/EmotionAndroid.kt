@file:JvmName("EmotionAndroid")

package io.github.rajumark.hoverfly.emotion

import android.content.Context

/** Kept so 1.x code (`Emotion(context)`) still compiles; the model no longer needs a [Context]. */
@Deprecated("The model is bundled without assets now; use Emotion().", ReplaceWith("Emotion()"))
@Suppress("UNUSED_PARAMETER", "FunctionName")
public fun Emotion(context: Context): Emotion = Emotion()
