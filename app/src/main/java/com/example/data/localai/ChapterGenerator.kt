package com.example.data.localai

import com.example.data.model.SubtitleBlock

object ChapterGenerator {
    /**
     * Generates standard text-formatted chapter lines (e.g. HH:MM:SS - Chapter Title)
     */
    fun generate(blocks: List<SubtitleBlock>): String {
        if (blocks.isEmpty()) return ""

        val totalDuration = blocks.last().endTimeMs
        val count = 3.coerceAtMost(blocks.size)
        val interval = blocks.size / count

        val sb = StringBuilder()
        for (i in 0 until count) {
            val blockIndex = (i * interval).coerceIn(0, blocks.size - 1)
            val block = blocks[blockIndex]
            val timeStr = formatTime(block.startTimeMs)
            val topic = when (i) {
                0 -> "Introduction & Setup"
                1 -> "Core Concepts & Architecture"
                else -> "Summary & Review"
            }
            sb.append("$timeStr - $topic\n")
        }
        return sb.toString().trim()
    }

    private fun formatTime(timeMs: Long): String {
        val sec = (timeMs / 1000) % 60
        val min = (timeMs / (1000 * 60)) % 60
        val hr = timeMs / (1000 * 60 * 60)
        return String.format("%02d:%02d:%02d", hr, min, sec)
    }
}
