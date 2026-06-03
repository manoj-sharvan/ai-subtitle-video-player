package com.example.data.localai

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.SubtitleApplication
import com.example.data.audio.AudioExtractor
import com.example.data.db.SubtitleCache
import com.example.data.model.VideoFile
import java.io.File

class SubtitleGenerationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "SubtitleGenWorker"
        const val KEY_VIDEO_ID = "VIDEO_ID"
        const val KEY_VIDEO_URI = "VIDEO_URI"
        const val KEY_VIDEO_TITLE = "VIDEO_TITLE"
        const val KEY_VIDEO_DURATION = "VIDEO_DURATION"
        const val KEY_LANGUAGE = "LANGUAGE"
        const val KEY_MODEL_NAME = "MODEL_NAME"
        const val KEY_NOISE_REDUCTION = "NOISE_REDUCTION"
        const val KEY_SPEAKER_ID = "SPEAKER_ID"

        const val KEY_PROGRESS = "PROGRESS"
        const val KEY_STATUS = "STATUS"
        const val KEY_FINISHED = "FINISHED"
    }

    override suspend fun doWork(): Result {
        val videoId = inputData.getLong(KEY_VIDEO_ID, -1L)
        val videoUriStr = inputData.getString(KEY_VIDEO_URI) ?: ""
        val videoTitle = inputData.getString(KEY_VIDEO_TITLE) ?: "video"
        val videoDuration = inputData.getLong(KEY_VIDEO_DURATION, 0L)
        val language = inputData.getString(KEY_LANGUAGE) ?: "English"
        val modelName = inputData.getString(KEY_MODEL_NAME) ?: "Whisper Base"
        val enableNoiseReduction = inputData.getBoolean(KEY_NOISE_REDUCTION, true)
        val enableSpeakerId = inputData.getBoolean(KEY_SPEAKER_ID, true)

        if (videoId == -1L || videoUriStr.isEmpty()) {
            return Result.failure(workDataOf(KEY_STATUS to "Invalid video inputs"))
        }

        Log.d(TAG, "Background subtitle job started for video: $videoTitle ($language)")
        updateProgress(5, "Scanning audio track...")

        val database = (applicationContext as SubtitleApplication).database
        val subtitleDao = database.subtitleDao()
        val cacheDao = database.subtitleCacheDao()

        // Step 1: Extract Audio
        updateProgress(10, "Extracting audio track...")
        val tempAudioFile = File(applicationContext.cacheDir, "extracted_audio_$videoId.m4a")
        if (tempAudioFile.exists()) {
            tempAudioFile.delete()
        }

        val extractionSuccess = try {
            AudioExtractor.extractAudio(applicationContext, Uri.parse(videoUriStr), tempAudioFile)
        } catch (e: Exception) {
            Log.e(TAG, "Audio demuxer execution failed", e)
            false
        }

        if (!extractionSuccess || tempAudioFile.length() <= 0) {
            updateProgress(20, "Failed to extract audio track.")
            return Result.failure(workDataOf(KEY_STATUS to "Failed to demux audio"))
        }

        updateProgress(25, "Audio extracted (Size: ${tempAudioFile.length() / 1024} KB). Running Whisper...")

        // Step 2: Transcribe in chunks (long video safety)
        val blocks = try {
            ChunkTranscriber.transcribeInChunks(
                context = applicationContext,
                audioFile = tempAudioFile,
                language = language,
                modelName = modelName,
                videoDurationMs = videoDuration
            ) { progressVal, statusStr ->
                kotlinx.coroutines.runBlocking {
                    updateProgress(progressVal, statusStr)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Transcription process failed", e)
            return Result.failure(workDataOf(KEY_STATUS to "Transcription failed: ${e.message}"))
        } finally {
            if (tempAudioFile.exists()) {
                tempAudioFile.delete()
            }
        }

        // Map blocks to actual videoId
        val finalBlocks = blocks.map { it.copy(videoId = videoId) }

        // Step 3: Format and Save SRT to Cache
        updateProgress(90, "Saving captions to local cache...")
        try {
            subtitleDao.deleteSubtitlesForVideo(videoId)
            subtitleDao.insertSubtitles(finalBlocks)

            // Save to absolute srt cache file path
            val cacheDir = File(applicationContext.filesDir, "subtitles")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val srtFile = File(cacheDir, "video_$videoId.srt")
            SrtGenerator.saveSrtFile(finalBlocks, srtFile)

            // Cache mapping in Room
            val cacheMapping = SubtitleCache(
                videoId = videoId,
                subtitlePath = srtFile.absolutePath,
                generatedAt = System.currentTimeMillis(),
                modelVersion = modelName
            )
            cacheDao.insertCache(cacheMapping)
        } catch (e: Exception) {
            Log.e(TAG, "Database transaction failed", e)
            return Result.failure(workDataOf(KEY_STATUS to "Failed saving to database: ${e.message}"))
        }

        updateProgress(100, "Ready to Play")
        Log.d(TAG, "Background subtitle job completed successfully.")
        return Result.success(workDataOf(KEY_FINISHED to true))
    }

    private suspend fun updateProgress(percent: Int, status: String) {
        setProgress(
            workDataOf(
                KEY_PROGRESS to percent,
                KEY_STATUS to status
            )
        )
    }
}
