package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoFile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val title: String,
    val duration: Long, // in ms
    val fileSize: Long, // in bytes
    val mimeType: String,
    val hasSubtitles: Boolean = false,
    val lastPlayedPosition: Long = 0, // in ms
    val addedAt: Long = System.currentTimeMillis(),
    val summary: String? = null,
    val keywords: List<String> = emptyList(),
    val chapters: List<ChapterMarker> = emptyList()
)

data class ChapterMarker(
    val title: String,
    val timeMs: Long
)
