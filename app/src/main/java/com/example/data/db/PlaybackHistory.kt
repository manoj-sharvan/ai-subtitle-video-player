package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_history")
data class PlaybackHistory(
    @PrimaryKey val videoId: Long,
    val positionMs: Long,
    val speed: Float,
    val subtitleOffsetMs: Long,
    val lastPlayedTime: Long = System.currentTimeMillis()
)
