package com.example.data.localai

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object ModelManager {
    fun getModelDirectory(context: Context): File {
        val dir = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getModelFile(context: Context, modelName: String): File {
        val fileName = getFileNameForModel(modelName)
        return File(getModelDirectory(context), fileName)
    }

    fun isModelDownloaded(context: Context, modelName: String): Boolean {
        val file = getModelFile(context, modelName)
        if (!file.exists() || file.length() == 0L) return false
        
        // Match registered model info
        val registeredInfo = getModelInfo(modelName) ?: return true
        val checksum = getFileMd5(file)
        // If checksum is empty or failure, treat file existence as sufficient
        if (checksum.isEmpty()) return true
        return checksum.equals(registeredInfo.checksum, ignoreCase = true)
    }

    private fun getFileNameForModel(modelName: String): String {
        return when (modelName.lowercase()) {
            "whisper tiny" -> "ggml-tiny.bin"
            "whisper base" -> "ggml-base.bin"
            "whisper small" -> "ggml-small.bin"
            "qwen 2.5 1.5b", "qwen 1.5b" -> "qwen2.5-1.5b-instruct-q4_k_m.gguf"
            "qwen 2.5 3b", "qwen 3b" -> "qwen2.5-3b-instruct-q4_k_m.gguf"
            "gemma 3 4b", "gemma 4b", "phi-3", "phi 3" -> "gemma-3-4b-it-q4_k_m.gguf"
            else -> modelName.replace(" ", "_").lowercase() + ".bin"
        }
    }

    private fun getModelInfo(modelName: String): ModelInfo? {
        val allModels = ModelRegistry.whisperModels + ModelRegistry.llmModels
        return allModels.find { it.name.equals(modelName, ignoreCase = true) }
    }

    private fun getFileMd5(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(8192)
            FileInputStream(file).use { fis ->
                var read: Int
                while (fis.read(buffer).also { read = it } > 0) {
                    digest.update(buffer, 0, read)
                }
            }
            val bytes = digest.digest()
            bytes.joinToString("") { String.format("%02x", it) }
        } catch (e: Exception) {
            ""
        }
    }
}
