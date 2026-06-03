package com.example.data.api

/**
 * DEPRECATED: Removed in favor of fully offline local AI pipelines (WhisperEngine + LocalLLMEngine).
 * This class is kept empty to prevent compilation issues for legacy modules.
 */
object GeminiSubtitleService {
    // Completely offline. No cloud connections.
    fun isApiKeyAvailable(): Boolean = false
}
