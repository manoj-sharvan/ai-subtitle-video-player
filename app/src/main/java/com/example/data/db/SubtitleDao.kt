package com.example.data.db

import androidx.room.*
import com.example.data.model.SubtitleBlock
import kotlinx.coroutines.flow.Flow

@Dao
interface SubtitleDao {
    @Query("SELECT * FROM subtitle_blocks WHERE videoId = :videoId ORDER BY startTimeMs ASC")
    fun getSubtitlesForVideo(videoId: Long): Flow<List<SubtitleBlock>>

    @Query("SELECT * FROM subtitle_blocks WHERE videoId = :videoId ORDER BY startTimeMs ASC")
    suspend fun getSubtitlesForVideoSync(videoId: Long): List<SubtitleBlock>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubtitles(subtitles: List<SubtitleBlock>)

    @Query("DELETE FROM subtitle_blocks WHERE videoId = :videoId")
    suspend fun deleteSubtitlesForVideo(videoId: Long)

    @Update
    suspend fun updateSubtitleBlock(block: SubtitleBlock)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubtitleBlock(block: SubtitleBlock): Long

    @Delete
    suspend fun deleteSubtitleBlock(block: SubtitleBlock)
}
