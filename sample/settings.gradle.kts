// Standalone consumer of the PUBLISHED Emotion artifacts, used to check a release before and after
// it goes live. It never sees the library source: io.github.rajumark comes only from
//   Maven Local   (default):          ./gradlew ...
//   Maven Central (-PemotionRepo=central): ./gradlew ... -PemotionRepo=central
rootProject.name = "emotion-sample"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

val fromCentral = providers.gradleProperty("emotionRepo").orNull == "central"

dependencyResolutionManagement {
    repositories {
        exclusiveContent {
            forRepository { if (fromCentral) mavenCentral() else mavenLocal() }
            filter { includeGroup("io.github.rajumark") }
        }
        google()
        mavenCentral()
    }
}

include(":shared")
include(":androidApp")
include(":desktopApp")
include(":webApp")
