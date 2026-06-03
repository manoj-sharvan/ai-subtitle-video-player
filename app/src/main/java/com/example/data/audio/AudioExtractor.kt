package com.example.data.audio

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

object AudioExtractor {
    private const val TAG = "AudioExtractor"

    fun extractAudio(context: Context, videoUri: Uri, outputFile: File): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(context, videoUri, null)
            val numTracks = extractor.trackCount
            var audioTrackIndex = -1
            var format: MediaFormat? = null

            // Search for the first audio track
            for (i in 0 until numTracks) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = f
                    break
                }
            }

            if (audioTrackIndex == -1 || format == null) {
                Log.e(TAG, "No audio track found in video")
                return false
            }

            extractor.selectTrack(audioTrackIndex)

            // MPEG_4 output works great for AAC / M4A streams
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val writeTrackIndex = muxer.addTrack(format)
            muxer.start()

            val maxBufferSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 1024)
            val buffer = ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = android.media.MediaCodec.BufferInfo()

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    break
                }
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(writeTrackIndex, buffer, bufferInfo)
                extractor.advance()
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract audio from video stream", e)
            return false
        } finally {
            try {
                extractor.release()
            } catch (e: Exception) {}
            try {
                muxer?.stop()
                muxer?.release()
            } catch (e: Exception) {}
        }
    }
}
