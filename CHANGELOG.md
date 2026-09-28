# Changelog

## 1.0.0

- First version: `Emotion(context).detect(text)` → `Result(emotions, all, basic, mood)`.
- Finds the emotions in an English message, several at once: the 27 GoEmotions emotions plus neutral, each with a
  score and its own tuned threshold. Also Ekman's six basic emotions and an overall mood (positive, negative,
  ambiguous, neutral).
- Pure Kotlin inference with no dependencies, int8 weights. minSdk 21. Hoverfly Community License.
