package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subtitle_settings")
data class SubtitleSettings(
    @PrimaryKey val id: Int = 1,
    val fontSize: Float = 18f,
    val fontFamily: String = "SansSerif",
    val fontColorHex: String = "#FFFFFF",
    val backgroundColorHex: String = "#000000",
    val backgroundOpacity: Float = 0.5f,
    val position: String = "BOTTOM",
    val isBold: Boolean = false,
    val themeMode: String = "SYSTEM" // "LIGHT", "DARK", "SYSTEM"
)
