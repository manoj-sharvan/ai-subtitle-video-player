package com.example.data.db

import androidx.room.*

@Dao
interface PlaybackHistoryDao {
    @Query("SELECT * FROM playback_history WHERE videoId = :videoId")
    suspend fun getHistoryForVideo(videoId: Long): PlaybackHistory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateHistory(history: PlaybackHistory)

    @Query("DELETE FROM playback_history WHERE videoId = :videoId")
    suspend fun deleteHistoryForVideo(videoId: Long)

    @Query("DELETE FROM playback_history")
    suspend fun clearAllHistory()
}
