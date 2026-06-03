package com.example.data.db

import androidx.room.*
import com.example.data.model.VideoFile
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY addedAt DESC")
    fun getAllVideos(): Flow<List<VideoFile>>

    @Query("""
        SELECT videos.* FROM videos 
        INNER JOIN playback_history ON videos.id = playback_history.videoId 
        ORDER BY playback_history.lastPlayedTime DESC 
        LIMIT 5
    """)
    fun getRecentVideos(): Flow<List<VideoFile>>

    @Query("SELECT * FROM videos WHERE id = :id")
    suspend fun getVideoById(id: Long): VideoFile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoFile): Long

    @Update
    suspend fun updateVideo(video: VideoFile)

    @Delete
    suspend fun deleteVideo(video: VideoFile)

    @Query("""
        SELECT * FROM videos 
        WHERE (:query = '' OR title LIKE '%' || :query || '%' OR folderName LIKE '%' || :query || '%' OR fileExtension LIKE '%' || :query || '%') 
          AND (:folderName IS NULL OR folderName = :folderName) 
          AND (:favoritesOnly = 0 OR isFavorite = 1)
        ORDER BY 
          CASE WHEN :sortBy = 'NAME_AZ' THEN title END ASC,
          CASE WHEN :sortBy = 'NAME_ZA' THEN title END DESC,
          CASE WHEN :sortBy = 'DATE_ADDED' THEN addedAt END DESC,
          CASE WHEN :sortBy = 'DURATION' THEN duration END DESC,
          CASE WHEN :sortBy = 'SIZE' THEN fileSize END DESC
    """)
    fun getVideosSearchSort(
        query: String,
        folderName: String?,
        favoritesOnly: Boolean,
        sortBy: String
    ): Flow<List<VideoFile>>

    @Query("SELECT DISTINCT folderName FROM videos ORDER BY folderName ASC")
    fun getDistinctFolders(): Flow<List<String>>

    @Query("DELETE FROM videos")
    suspend fun clearAll()
}
