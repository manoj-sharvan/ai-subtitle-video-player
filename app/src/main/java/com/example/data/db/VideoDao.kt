package com.example.data.db

import androidx.room.*
import com.example.data.model.VideoFile
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY addedAt DESC")
    fun getAllVideos(): Flow<List<VideoFile>>

    @Query("SELECT * FROM videos ORDER BY addedAt DESC LIMIT 5")
    fun getRecentVideos(): Flow<List<VideoFile>>

    @Query("SELECT * FROM videos WHERE id = :id")
    suspend fun getVideoById(id: Long): VideoFile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoFile): Long

    @Update
    suspend fun updateVideo(video: VideoFile)

    @Delete
    suspend fun deleteVideo(video: VideoFile)

    @Query("DELETE FROM videos")
    suspend fun clearAll()
}
