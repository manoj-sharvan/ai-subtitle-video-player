package com.example.data.localai

import com.example.data.model.SubtitleBlock
import java.io.File

object SrtGenerator {
    /**
     * Compiles list of subtitle blocks into a standard formatted SRT string.
     */
    fun generateSrtContent(blocks: List<SubtitleBlock>): String {
        val validatedBlocks = mutableListOf<SubtitleBlock>()
        var prevEndTimeMs = 0L

        blocks.sortedBy { it.startTimeMs }.forEach { block ->
            var start = block.startTimeMs
            var end = block.endTimeMs

            // 1. Validation check for negative times
            if (start < 0L) start = 0L
            if (end < 0L) end = 0L

            // 2. Validation check for start < end
            if (end <= start) {
                end = start + 1000L // Force 1-second duration if invalid
            }

            // 3. Validation check for overlapping intervals (self-healing)
            if (start < prevEndTimeMs) {
                start = prevEndTimeMs
                if (end <= start) {
                    end = start + 1000L
                }
            }

            validatedBlocks.add(
                block.copy(
                    startTimeMs = start,
                    endTimeMs = end
                )
            )
            prevEndTimeMs = end
        }

        val sb = StringBuilder()
        validatedBlocks.forEach { block ->
            sb.append("${block.index}\n")
            sb.append("${formatTime(block.startTimeMs)} --> ${formatTime(block.endTimeMs)}\n")
            if (block.speaker != null) {
                sb.append("[${block.speaker}]: ")
            }
            sb.append("${block.text}\n\n")
        }
        return sb.toString()
    }

    /**
     * Compiles and saves subtitle blocks to a local SRT cache file.
     */
    fun saveSrtFile(blocks: List<SubtitleBlock>, outputFile: File): Boolean {
        return try {
            val content = generateSrtContent(blocks)
            outputFile.writeText(content)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun formatTime(timeMs: Long): String {
        val ms = timeMs % 1000
        val sec = (timeMs / 1000) % 60
        val min = (timeMs / (1000 * 60)) % 60
        val hr = timeMs / (1000 * 60 * 60)
        return String.format("%02d:%02d:%02d,%03d", hr, min, sec, ms)
    }
}
