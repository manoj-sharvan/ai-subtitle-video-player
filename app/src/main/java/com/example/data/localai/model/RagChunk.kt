package com.example.data.localai.model

data class RagChunk(
    val id: String,
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val tokenWeights: Map<String, Float> = emptyMap()
)
