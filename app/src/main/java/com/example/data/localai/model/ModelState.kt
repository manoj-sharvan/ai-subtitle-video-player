package com.example.data.localai.model

sealed class ModelState {
    object NotDownloaded : ModelState()
    data class Downloading(val progress: Int) : ModelState()
    object Verifying : ModelState()
    object Ready : ModelState()
    data class Failed(val error: String) : ModelState()
}
