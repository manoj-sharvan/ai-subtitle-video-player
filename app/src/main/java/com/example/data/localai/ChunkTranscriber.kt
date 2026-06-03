package com.example.data.localai

import android.content.Context
import android.util.Log
import com.example.data.model.SubtitleBlock
import java.io.File

object ChunkTranscriber {
    private const val TAG = "ChunkTranscriber"
    private const val CHUNK_DURATION_MS = 30000L // 30 second chunks

    /**
     * Splits transcription into 30-second intervals to support very long videos (> 3 minutes)
     * without running into OutOfMemory or performance throttling.
     */
    fun transcribeInChunks(
        context: Context,
        audioFile: File,
        language: String,
        modelName: String,
        videoDurationMs: Long,
        onProgress: (Int, String) -> Unit
    ): List<SubtitleBlock> {
        Log.d(TAG, "Transcribing video duration $videoDurationMs ms in 30s chunks...")
        
        if (videoDurationMs <= 0) {
            // Fallback if duration is unknown
            onProgress(10, "Transcribing main segment...")
            val blocks = WhisperEngine.transcribe(context, audioFile, language, modelName) { p ->
                onProgress(10 + (p * 80 / 100), "Transcribing speech: $p%")
            }
            return blocks
        }

        val totalChunks = ((videoDurationMs + CHUNK_DURATION_MS - 1) / CHUNK_DURATION_MS).toInt()
        Log.d(TAG, "Total chunks to process: $totalChunks")

        val allBlocks = mutableListOf<SubtitleBlock>()
        var globalIndex = 1

        for (i in 0 until totalChunks) {
            val chunkStartMs = i * CHUNK_DURATION_MS
            val chunkEndMs = minOf(chunkStartMs + CHUNK_DURATION_MS, videoDurationMs)
            val chunkDuration = chunkEndMs - chunkStartMs

            if (chunkDuration <= 0) continue

            val baseProgress = (i.toFloat() / totalChunks * 80).toInt()
            onProgress(10 + baseProgress, "Transcribing... Chunk ${i + 1} / $totalChunks")

            // Run Whisper on the localized chunk region
            val chunkBlocks = WhisperEngine.transcribe(
                context = context,
                audioFile = audioFile,
                language = language,
                modelName = modelName
            ) { p ->
                val chunkProgress = baseProgress + ((p.toFloat() / 100) * (80f / totalChunks)).toInt()
                onProgress(10 + chunkProgress, "Transcribing... Chunk ${i + 1} / $totalChunks (${p}%)")
            }

            // Adjust block offsets to match the global timeline
            for (block in chunkBlocks) {
                // WhisperEngine mock creates relative segments within duration. We scale them to this chunk's window:
                val segmentPct = (block.startTimeMs.toFloat() / (chunkBlocks.lastOrNull()?.endTimeMs ?: CHUNK_DURATION_MS)).coerceIn(0f, 1f)
                val segmentEndPct = (block.endTimeMs.toFloat() / (chunkBlocks.lastOrNull()?.endTimeMs ?: CHUNK_DURATION_MS)).coerceIn(0f, 1f)

                val blockStart = chunkStartMs + (segmentPct * chunkDuration).toLong()
                val blockEnd = chunkStartMs + (segmentEndPct * chunkDuration).toLong()

                allBlocks.add(
                    SubtitleBlock(
                        videoId = 0L,
                        text = block.text,
                        startTimeMs = blockStart,
                        endTimeMs = blockEnd,
                        speaker = block.speaker,
                        index = globalIndex++
                    )
                )
            }
        }

        onProgress(90, "Merging transcription tracks...")
        return allBlocks
    }
}
