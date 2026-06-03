package com.example.data.db

import androidx.room.TypeConverter
import com.example.data.model.ChapterMarker

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return value.joinToString(";;;")
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        if (value.isEmpty()) return emptyList()
        return value.split(";;;")
    }

    @TypeConverter
    fun fromChaptersList(value: List<ChapterMarker>): String {
        return value.joinToString(";;;") { "${it.title}|||${it.timeMs}" }
    }

    @TypeConverter
    fun toChaptersList(value: String): List<ChapterMarker> {
        if (value.isEmpty()) return emptyList()
        return value.split(";;;").mapNotNull {
            val parts = it.split("|||")
            if (parts.size == 2) {
                ChapterMarker(parts[0], parts[1].toLongOrNull() ?: 0L)
            } else {
                null
            }
        }
    }
}
