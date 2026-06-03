package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.model.SubtitleBlock
import com.example.data.model.VideoFile

@Database(entities = [VideoFile::class, SubtitleBlock::class, PlaybackHistory::class, SubtitleCache::class, SubtitleSettings::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun subtitleDao(): SubtitleDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao
    abstract fun subtitleCacheDao(): SubtitleCacheDao
    abstract fun subtitleSettingsDao(): SubtitleSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ai_subtitle_player_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
