package com.example.data.localai

import android.content.Context
import android.util.Log
import com.example.data.localai.model.ModelState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object ModelDownloader {
    private const val TAG = "ModelDownloader"

    suspend fun downloadModel(
        context: Context,
        modelName: String,
        onStateChanged: (ModelState) -> Unit
    ) {
        val allModels = ModelRegistry.whisperModels + ModelRegistry.llmModels
        val modelInfo = allModels.find { it.name.equals(modelName, ignoreCase = true) }
        if (modelInfo == null) {
            onStateChanged(ModelState.Failed("Model $modelName not found in registry"))
            return
        }

        val targetFile = ModelManager.getModelFile(context, modelName)
        if (ModelManager.isModelDownloaded(context, modelName)) {
            onStateChanged(ModelState.Ready)
            return
        }

        withContext(Dispatchers.IO) {
            try {
                onStateChanged(ModelState.Downloading(0))
                val url = URL(modelInfo.url)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    onStateChanged(ModelState.Failed("Server returned code ${connection.responseCode}"))
                    return@withContext
                }

                val fileLength = connection.contentLength
                val tempFile = File(targetFile.parentFile, targetFile.name + ".tmp")
                if (tempFile.exists()) {
                    tempFile.delete()
                }

                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val data = ByteArray(4096)
                        var total: Long = 0
                        var count: Int
                        var lastProgress = 0
                        while (input.read(data).also { count = it } != -1) {
                            total += count
                            output.write(data, 0, count)
                            if (fileLength > 0) {
                                val progress = ((total * 100) / fileLength).toInt()
                                if (progress != lastProgress) {
                                    lastProgress = progress
                                    onStateChanged(ModelState.Downloading(progress))
                                }
                            }
                        }
                    }
                }

                onStateChanged(ModelState.Verifying)
                if (tempFile.exists()) {
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                    if (tempFile.renameTo(targetFile)) {
                        onStateChanged(ModelState.Ready)
                    } else {
                        onStateChanged(ModelState.Failed("Failed to rename temporary download file"))
                    }
                } else {
                    onStateChanged(ModelState.Failed("Downloaded file not found"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading model $modelName", e)
                onStateChanged(ModelState.Failed(e.message ?: "Unknown download error"))
            }
        }
    }
}
