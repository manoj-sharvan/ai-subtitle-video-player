package com.example.data.db

import androidx.room.*

@Dao
interface SubtitleSettingsDao {
    @Query("SELECT * FROM subtitle_settings WHERE id = 1")
    suspend fun getSettings(): SubtitleSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: SubtitleSettings)
}
