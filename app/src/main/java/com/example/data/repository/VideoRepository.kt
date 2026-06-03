package com.example.data.repository

import com.example.data.db.VideoDao
import com.example.data.model.VideoFile
import kotlinx.coroutines.flow.Flow

class VideoRepository(private val videoDao: VideoDao) {
    val allVideos: Flow<List<VideoFile>> = videoDao.getAllVideos()
    val recentVideos: Flow<List<VideoFile>> = videoDao.getRecentVideos()

    suspend fun getVideoById(id: Long): VideoFile? {
        return videoDao.getVideoById(id)
    }

    suspend fun insertVideo(video: VideoFile): Long {
        return videoDao.insertVideo(video)
    }

    suspend fun updateVideo(video: VideoFile) {
        videoDao.updateVideo(video)
    }

    suspend fun deleteVideo(video: VideoFile) {
        videoDao.deleteVideo(video)
    }

    fun getVideosSearchSort(query: String, folderName: String?, favoritesOnly: Boolean, sortBy: String): Flow<List<VideoFile>> {
        return videoDao.getVideosSearchSort(query, folderName, favoritesOnly, sortBy)
    }

    fun getDistinctFolders(): Flow<List<String>> {
        return videoDao.getDistinctFolders()
    }

    suspend fun clearAll() {
        videoDao.clearAll()
    }
}
