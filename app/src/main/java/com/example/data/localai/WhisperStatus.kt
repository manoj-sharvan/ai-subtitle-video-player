package com.example.data.localai

sealed class WhisperStatus {
    object Ready : WhisperStatus()
    data class ModelMissing(val modelPath: String) : WhisperStatus()
    object NativeLibraryMissing : WhisperStatus()
    data class InitializationFailed(val error: String) : WhisperStatus()
}
