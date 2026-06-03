package com.example.data.db

import androidx.room.*

@Dao
interface SubtitleCacheDao {
    @Query("SELECT * FROM subtitle_cache WHERE videoId = :videoId")
    suspend fun getCacheForVideo(videoId: Long): SubtitleCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(cache: SubtitleCache)

    @Query("DELETE FROM subtitle_cache WHERE videoId = :videoId")
    suspend fun deleteCacheForVideo(videoId: Long)

    @Query("DELETE FROM subtitle_cache")
    suspend fun clearAllCache()
}
