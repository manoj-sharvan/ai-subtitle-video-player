package com.example.data.localai

sealed class SubtitleError(val message: String) {
    object StorageFull : SubtitleError("Insufficient storage space on device.")
    object ModelMissing : SubtitleError("Required speech model is missing. Please download it first.")
    object LowMemory : SubtitleError("Device is low on memory. Processing paused.")
    object UnsupportedCodec : SubtitleError("Video uses an unsupported audio/video codec.")
    object TranscriptionFailed : SubtitleError("Speech recognition failed.")
    object ExtractionFailed : SubtitleError("Failed to extract audio track from video.")
    object Cancelled : SubtitleError("Subtitle generation was cancelled by the user.")
}
