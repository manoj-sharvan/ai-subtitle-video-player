package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "videos",
    indices = [
        Index(value = ["title"]),
        Index(value = ["folderName"]),
        Index(value = ["addedAt"])
    ]
)
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
    val chapters: List<ChapterMarker> = emptyList(),
    val path: String = "",
    val folderName: String = "",
    val fileExtension: String = "",
    val isFavorite: Boolean = false
)

data class ChapterMarker(
    val title: String,
    val timeMs: Long
)

