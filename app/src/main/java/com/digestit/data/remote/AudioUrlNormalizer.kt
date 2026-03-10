package com.digestit.data.remote

fun normalizeAudioPath(audioUrl: String?): String? {
    val trimmed = audioUrl?.trim().orEmpty()
    if (trimmed.isBlank()) return null
    return if (trimmed.startsWith("/api/v1/")) {
        trimmed.removePrefix("/api")
    } else {
        trimmed
    }
}
