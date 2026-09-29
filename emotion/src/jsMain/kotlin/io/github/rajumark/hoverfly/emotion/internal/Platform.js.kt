package io.github.rajumark.hoverfly.emotion.internal

internal actual fun nfkc(s: String): String = s.asDynamic().normalize("NFKC") as String
