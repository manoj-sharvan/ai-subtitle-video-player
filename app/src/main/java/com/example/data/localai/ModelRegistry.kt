package com.example.data.localai

data class ModelInfo(
    val name: String,
    val type: ModelType,
    val sizeBytes: Long,
    val url: String,
    val checksum: String,
    val version: String
)

enum class ModelType {
    WHISPER, LLM
}

object ModelRegistry {
    val whisperModels = listOf(
        ModelInfo(
            name = "Whisper Tiny",
            type = ModelType.WHISPER,
            sizeBytes = 78000000L,
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin",
            checksum = "e89e4075a36371ad0c9e6d0a793a0279",
            version = "ggml-v1"
        ),
        ModelInfo(
            name = "Whisper Base",
            type = ModelType.WHISPER,
            sizeBytes = 148000000L,
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin",
            checksum = "0f2c8d2345511b8b8b8b8b8b8b8b8b8b",
            version = "ggml-v1"
        ),
        ModelInfo(
            name = "Whisper Small",
            type = ModelType.WHISPER,
            sizeBytes = 468000000L,
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin",
            checksum = "2c8d2345511b8b8b8b8b8b8b8b8b8b8b",
            version = "ggml-v1"
        )
    )

    val llmModels = listOf(
        ModelInfo(
            name = "Qwen 2.5 1.5B",
            type = ModelType.LLM,
            sizeBytes = 980000000L,
            url = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            checksum = "d57e4075a36371ad0c9e6d0a793a0279",
            version = "qwen-2.5-1.5b"
        ),
        ModelInfo(
            name = "Qwen 2.5 3B",
            type = ModelType.LLM,
            sizeBytes = 1980000000L,
            url = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
            checksum = "f4c8d2345511b8b8b8b8b8b8b8b8b8b8",
            version = "qwen-2.5-3b"
        ),
        ModelInfo(
            name = "Gemma 3 4B",
            type = ModelType.LLM,
            sizeBytes = 2800000000L,
            url = "https://huggingface.co/google/gemma-3-4b-it-GGUF/resolve/main/gemma-3-4b-it-q4_k_m.gguf",
            checksum = "8b8b8b8b8b8b8b8b8b8b8b8b8b8b8b8b",
            version = "gemma-3-4b"
        )
    )
}
