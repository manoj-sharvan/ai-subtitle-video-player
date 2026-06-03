package com.example.data.localai

enum class WhisperModel {
    TINY, BASE, SMALL
}

data class WhisperConfig(
    val modelType: WhisperModel,
    val language: String = "English",
    val translateToEnglish: Boolean = false
)
