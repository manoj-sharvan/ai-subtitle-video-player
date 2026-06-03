package com.example.data.localai.model

sealed interface GenerationState {
    object Idle : GenerationState
    object ExtractingAudio : GenerationState
    object Chunking : GenerationState
    object Transcribing : GenerationState
    object GeneratingSrt : GenerationState
    object RefiningSubtitle : GenerationState
    object Completed : GenerationState
    data class Error(val message: String) : GenerationState
    object Cancelled : GenerationState
}
