package com.bigdictaphone.app.data

import kotlinx.serialization.Serializable

@Serializable
enum class TranscriptionMode(val title: String) {
    LOCAL("On this phone"),
    CLOUD("Gemini cloud")
}
