package com.example

import android.app.Application
import com.example.data.db.AppDatabase
import com.example.data.repository.SubtitleRepository
import com.example.data.repository.VideoRepository

class SubtitleApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var videoRepository: VideoRepository
        private set

    lateinit var subtitleRepository: SubtitleRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        videoRepository = VideoRepository(database.videoDao())
        subtitleRepository = SubtitleRepository(database.subtitleDao())
    }
}
