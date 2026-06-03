package com.example.data.localai.model

data class TranscriptSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String
)
