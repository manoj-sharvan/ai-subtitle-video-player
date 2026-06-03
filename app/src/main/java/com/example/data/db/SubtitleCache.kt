package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subtitle_cache")
data class SubtitleCache(
    @PrimaryKey val videoId: Long,
    val subtitlePath: String,
    val generatedAt: Long = System.currentTimeMillis(),
    val modelVersion: String,
    val sourceVideoLastModified: Long = 0L
)
