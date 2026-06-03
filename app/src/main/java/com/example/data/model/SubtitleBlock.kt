package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subtitle_blocks")
data class SubtitleBlock(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: Long,
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val speaker: String? = null,
    val index: Int
) {
    val durationMs: Long get() = endTimeMs - startTimeMs

    fun formattedTime(ms: Long): String {
        val hours = ms / 3600000
        val minutes = (ms % 3600000) / 60000
        val seconds = (ms % 60000) / 1000
        val milliseconds = ms % 1000
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, milliseconds)
    }
}
