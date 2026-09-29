# Changelog

## 2.0.0

- Kotlin Multiplatform: Android, JVM desktop, iOS (arm64 device + simulator), macOS arm64,
  JavaScript and WebAssembly, published to Maven Central as `io.github.rajumark:emotion`.
- New constructor `Emotion()`: the model ships inside the library on every platform, so no
  `Context` is needed. `Emotion(context)` still compiles on Android (deprecated).
- Same model and same results as 1.x; parity with the reference (184 vectors) is tested on every target.
- The sample is now a Compose Multiplatform app (Android, desktop, iOS) plus a web page (JS and Wasm).

## 1.0.0

- First version: `Emotion(context).detect(text)` → `Result(emotions, all, basic, mood)`.
- Finds the emotions in an English message, several at once: the 27 GoEmotions emotions plus neutral, each with a
  score and its own tuned threshold. Also Ekman's six basic emotions and an overall mood (positive, negative,
  ambiguous, neutral).
- Pure Kotlin inference with no dependencies, int8 weights. minSdk 21. Hoverfly Community License.
