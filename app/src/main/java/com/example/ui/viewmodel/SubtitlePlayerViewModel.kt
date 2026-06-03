package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.SubtitleApplication
import com.example.data.localai.LocalLLMEngine
import com.example.data.localai.LocalRagPipeline
import com.example.data.localai.model.RagChunk
import com.example.data.model.ChapterMarker
import com.example.data.model.SubtitleBlock
import com.example.data.model.SubtitlePosition
import com.example.data.model.SubtitleStyle
import com.example.data.model.VideoFile
import com.example.data.repository.SubtitleRepository
import com.example.data.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import android.provider.MediaStore
import android.content.ContentUris
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.data.db.PlaybackHistory
import com.example.data.db.SubtitleSettings
import com.example.data.localai.model.ModelState
import com.example.data.localai.ModelRegistry
import com.example.data.localai.ModelManager
import com.example.data.localai.ModelDownloader

private data class VideoSearchParams(val query: String, val folder: String?, val favOnly: Boolean, val sort: String)

@kotlinx.coroutines.ExperimentalCoroutinesApi
class SubtitlePlayerViewModel(
    application: Application,
    private val videoRepository: VideoRepository,
    private val subtitleRepository: SubtitleRepository
) : AndroidViewModel(application) {

    // --- DB DAO Access ---
    private val database = getApplication<SubtitleApplication>().database
    private val historyDao = database.playbackHistoryDao()
    private val cacheDao = database.subtitleCacheDao()
    private val settingsDao = database.subtitleSettingsDao()

    // --- Video Library Streams ---
    val allVideos: StateFlow<List<VideoFile>> = videoRepository.allVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentVideos: StateFlow<List<VideoFile>> = videoRepository.recentVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Search, Filter & Sort States ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedFolder = MutableStateFlow<String?>(null)
    val selectedFolder = _selectedFolder.asStateFlow()

    private val _showFavoritesOnly = MutableStateFlow(false)
    val showFavoritesOnly = _showFavoritesOnly.asStateFlow()

    private val _sortBy = MutableStateFlow("DATE_ADDED") // "NAME_AZ", "NAME_ZA", "DATE_ADDED", "DURATION", "SIZE"
    val sortBy = _sortBy.asStateFlow()

    val filteredVideos: StateFlow<List<VideoFile>> = combine(
        searchQuery,
        selectedFolder,
        showFavoritesOnly,
        sortBy
    ) { query, folder, favOnly, sort ->
        VideoSearchParams(query, folder, favOnly, sort)
    }.flatMapLatest { params ->
        videoRepository.getVideosSearchSort(params.query, params.folder, params.favOnly, params.sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val distinctFolders: StateFlow<List<String>> = videoRepository.getDistinctFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Subtitle Track for current video ---
    private val _currentSubtitles = MutableStateFlow<List<SubtitleBlock>>(emptyList())
    val currentSubtitles: StateFlow<List<SubtitleBlock>> = _currentSubtitles.asStateFlow()

    // --- Active Subtitle Block (updated by current player timeMs) ---
    private val _activeSubtitle = MutableStateFlow<SubtitleBlock?>(null)
    val activeSubtitle: StateFlow<SubtitleBlock?> = _activeSubtitle.asStateFlow()

    // --- Custom Subtitle Styling State ---
    private val _subtitleStyle = MutableStateFlow(SubtitleStyle())
    val subtitleStyle: StateFlow<SubtitleStyle> = _subtitleStyle.asStateFlow()

    // --- Player Playback State ---
    private val _selectedVideo = MutableStateFlow<VideoFile?>(null)
    val selectedVideo: StateFlow<VideoFile?> = _selectedVideo.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _subtitleOffsetMs = MutableStateFlow(0L)
    val subtitleOffsetMs: StateFlow<Long> = _subtitleOffsetMs.asStateFlow()

    private val _subtitlesEnabled = MutableStateFlow(true)
    val subtitlesEnabled: StateFlow<Boolean> = _subtitlesEnabled.asStateFlow()

    // --- Transcription State ---
    private val _isTranscribing = MutableStateFlow(false)
    val isTranscribing: StateFlow<Boolean> = _isTranscribing.asStateFlow()

    private val _transcriptionProgress = MutableStateFlow(0)
    val transcriptionProgress: StateFlow<Int> = _transcriptionProgress.asStateFlow()

    private val _transcriptionStep = MutableStateFlow("")
    val transcriptionStep: StateFlow<String> = _transcriptionStep.asStateFlow()

    // --- AI Feature Output States ---
    private val _isProcessingAI = MutableStateFlow(false)
    val isProcessingAI: StateFlow<Boolean> = _isProcessingAI.asStateFlow()

    private val _aiResultText = MutableStateFlow<String?>(null)
    val aiResultText: StateFlow<String?> = _aiResultText.asStateFlow()

    private val _themeMode = MutableStateFlow("SYSTEM")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _whisperModelStates = MutableStateFlow<Map<String, ModelState>>(emptyMap())
    val whisperModelStates: StateFlow<Map<String, ModelState>> = _whisperModelStates.asStateFlow()

    private val _llmModelStates = MutableStateFlow<Map<String, ModelState>>(emptyMap())
    val llmModelStates: StateFlow<Map<String, ModelState>> = _llmModelStates.asStateFlow()

    // --- Chat Message structure ---
    data class ChatMessage(
        val text: String,
        val isUser: Boolean,
        val timestampMs: Long = System.currentTimeMillis()
    )

    private val localRagPipeline = LocalRagPipeline()

    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory.asStateFlow()

    private val _ragSearchResults = MutableStateFlow<List<RagChunk>>(emptyList())
    val ragSearchResults: StateFlow<List<RagChunk>> = _ragSearchResults.asStateFlow()

    fun askQuestionAboutVideo(question: String) {
        if (question.trim().isEmpty()) return
        
        val userMsg = ChatMessage(text = question, isUser = true)
        _chatHistory.update { it + userMsg }
        
        viewModelScope.launch {
            _isProcessingAI.value = true
            
            // Search RAG chunks
            val matchingChunks = localRagPipeline.search(question)
            
            // Generate answer
            val answer = LocalLLMEngine.answerQuestion(question, matchingChunks)
            
            val aiMsg = ChatMessage(text = answer, isUser = false)
            _chatHistory.update { it + aiMsg }
            _isProcessingAI.value = false
        }
    }

    fun searchSubtitlesLocal(query: String) {
        viewModelScope.launch {
            val results = localRagPipeline.search(query)
            _ragSearchResults.value = results
        }
    }

    fun clearChatHistory() {
        _chatHistory.value = emptyList()
    }

    fun refreshModelStatuses() {
        val whisperMap = mutableMapOf<String, ModelState>()
        ModelRegistry.whisperModels.forEach { info ->
            val isDownloaded = ModelManager.isModelDownloaded(getApplication(), info.name)
            whisperMap[info.name] = if (isDownloaded) ModelState.Ready else ModelState.NotDownloaded
        }
        _whisperModelStates.value = whisperMap

        val llmMap = mutableMapOf<String, ModelState>()
        ModelRegistry.llmModels.forEach { info ->
            val isDownloaded = ModelManager.isModelDownloaded(getApplication(), info.name)
            llmMap[info.name] = if (isDownloaded) ModelState.Ready else ModelState.NotDownloaded
        }
        _llmModelStates.value = llmMap
    }

    fun downloadModel(modelName: String) {
        viewModelScope.launch {
            ModelDownloader.downloadModel(getApplication(), modelName) { state ->
                val isWhisper = ModelRegistry.whisperModels.any { it.name.equals(modelName, ignoreCase = true) }
                if (isWhisper) {
                    val current = _whisperModelStates.value.toMutableMap()
                    current[modelName] = state
                    _whisperModelStates.value = current
                } else {
                    val current = _llmModelStates.value.toMutableMap()
                    current[modelName] = state
                    _llmModelStates.value = current
                }
            }
        }
    }

    init {
        scanLocalVideos()
        loadSubtitleSettings()
        refreshModelStatuses()
    }

    private fun loadSubtitleSettings() {
        viewModelScope.launch {
            val settings = settingsDao.getSettings() ?: SubtitleSettings()
            _themeMode.value = settings.themeMode
            _subtitleStyle.value = SubtitleStyle(
                fontSizeSp = settings.fontSize,
                fontFamily = settings.fontFamily,
                textColorHex = settings.fontColorHex,
                backgroundColorHex = settings.backgroundColorHex,
                backgroundOpacity = settings.backgroundOpacity,
                position = SubtitlePosition.valueOf(settings.position)
            )
        }
    }

    private suspend fun prepopulateDatabase() {
        val sampleVideos = listOf(
            VideoFile(
                title = "Big Buck Bunny (Sintel Animation Studio)",
                uri = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                duration = 596000L,
                fileSize = 10500000L,
                mimeType = "video/mp4",
                hasSubtitles = true,
                summary = "An animated comedy following an overgrown rabbit who decides to exact comical revenge on three bullying woodland pests who killed a butterfly and disrupted his peaceful day."
            ),
            VideoFile(
                title = "Sintel Movie Trailer (Blender Foundation)",
                uri = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                duration = 52000L,
                fileSize = 7500000L,
                mimeType = "video/mp4",
                hasSubtitles = true,
                summary = "Sintel, a lonely young woman, searches for her baby dragon companion, scales freezing cliffs, battles brutal obstacles, only to discover a heartbreaking truth upon rescue."
            ),
            VideoFile(
                title = "Tears of Steel CGI Sci-Fi (VFX Showcase)",
                uri = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                duration = 734000L,
                fileSize = 14200000L,
                mimeType = "video/mp4",
                hasSubtitles = false,
                summary = "A sci-fi action story set in a dystopian future where VFX specialists try to save Amsterdam from a giant, destructive robot using projection mapping."
            )
        )
        sampleVideos.forEach {
            val videoId = videoRepository.insertVideo(it)
            if (it.hasSubtitles) {
                // Pre-generate English subtitles for samples so the user has immediate playability
                val sampleSubs = listOf(
                    SubtitleBlock(videoId = videoId, text = "[Background Synth Music plays]", startTimeMs = 500L, endTimeMs = 4000L, speaker = "Narrator", index = 1),
                    SubtitleBlock(videoId = videoId, text = "Welcome to the premium AI Subtitle Video Player showcase.", startTimeMs = 4500L, endTimeMs = 9000L, speaker = "Speaker A", index = 2),
                    SubtitleBlock(videoId = videoId, text = "This app demonstrates high-performance caption syncing in Jetpack Compose.", startTimeMs = 9500L, endTimeMs = 13500L, speaker = "Speaker A", index = 3),
                    SubtitleBlock(videoId = videoId, text = "You can customize subtitle alignments, sizes, colors and styles.", startTimeMs = 14000L, endTimeMs = 18500L, speaker = "Speaker B", index = 4),
                    SubtitleBlock(videoId = videoId, text = "Translate with Gemini, segment speech, or extract high-value markers.", startTimeMs = 19000L, endTimeMs = 24000L, speaker = "Speaker B", index = 5)
                )
                subtitleRepository.insertSubtitles(sampleSubs)
            }
        }
    }

    fun scanLocalVideos() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_VIDEO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            
            val hasPermission = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                Log.d("ViewModel", "No permission to scan local videos")
                return@launch
            }
            
            val localVideos = mutableListOf<VideoFile>()
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.DATA
            )
            
            val queryUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            
            try {
                context.contentResolver.query(
                    queryUri,
                    projection,
                    null,
                    null,
                    "${MediaStore.Video.Media.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                    val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                    val dataColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                    
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val name = cursor.getString(nameColumn)
                        val duration = cursor.getLong(durationColumn)
                        val size = cursor.getLong(sizeColumn)
                        val mimeType = cursor.getString(mimeTypeColumn) ?: "video/*"
                        val path = if (dataColumn != -1) cursor.getString(dataColumn) ?: "" else ""
                        val file = java.io.File(path)
                        val folderName = file.parentFile?.name ?: "Unknown"
                        val fileExtension = file.extension
                        
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            id
                        ).toString()
                        
                        localVideos.add(
                            VideoFile(
                                title = name,
                                uri = contentUri,
                                duration = duration,
                                fileSize = size,
                                mimeType = mimeType,
                                hasSubtitles = false,
                                path = path,
                                folderName = folderName,
                                fileExtension = fileExtension,
                                isFavorite = false
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Failed to query MediaStore", e)
            }
            
            val existingVideos = videoRepository.allVideos.first()
            val existingUris = existingVideos.map { it.uri }.toSet()
            
            // Delete hardcoded demo videos if we found actual local videos
            if (localVideos.isNotEmpty()) {
                existingVideos.forEach { video ->
                    if (video.uri.startsWith("https://commondatastorage.googleapis.com/")) {
                        videoRepository.deleteVideo(video)
                    }
                }
            }
            
            val localUris = localVideos.map { it.uri }.toSet()
            
            // Delete videos that no longer exist
            existingVideos.forEach { video ->
                if (!video.uri.startsWith("https://commondatastorage.googleapis.com/") && video.uri !in localUris) {
                    videoRepository.deleteVideo(video)
                }
            }
            
            // Add new videos incrementally
            localVideos.forEach { video ->
                if (video.uri !in existingUris) {
                    videoRepository.insertVideo(video)
                }
            }
        }
    }

    // --- Library Search, Sort, Filter Actions ---
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectFolder(folder: String?) {
        _selectedFolder.value = folder
    }

    fun toggleFavoritesOnly(enabled: Boolean) {
        _showFavoritesOnly.value = enabled
    }

    fun updateSortOrder(sort: String) {
        _sortBy.value = sort
    }

    fun toggleFavorite(video: VideoFile) {
        viewModelScope.launch {
            val updated = video.copy(isFavorite = !video.isFavorite)
            videoRepository.updateVideo(updated)
            if (_selectedVideo.value?.id == video.id) {
                _selectedVideo.value = updated
            }
        }
    }

    // --- Library Actions ---
    fun selectVideo(video: VideoFile) {
        _selectedVideo.value = video
        _activeSubtitle.value = null
        _playbackSpeed.value = 1.0f
        _subtitleOffsetMs.value = 0L
        clearChatHistory()
        
        viewModelScope.launch {
            subtitleRepository.getSubtitlesForVideo(video.id).collect { blocks ->
                _currentSubtitles.value = blocks
                localRagPipeline.indexSubtitles(blocks)
            }
        }

        viewModelScope.launch {
            historyDao.getHistoryForVideo(video.id)?.let { history ->
                _playbackSpeed.value = history.speed
                _subtitleOffsetMs.value = history.subtitleOffsetMs
            }
        }
    }

    fun addCustomVideo(title: String, url: String) {
        viewModelScope.launch {
            val newVideo = VideoFile(
                title = title,
                uri = url,
                duration = 180000L, // default 3 min size until metadata loaded
                fileSize = 5120000L,
                mimeType = "video/mp4",
                hasSubtitles = false
            )
            val id = videoRepository.insertVideo(newVideo)
            // auto-select new video
            selectVideo(newVideo.copy(id = id))
        }
    }

    fun updateVideoLastPlayed(videoId: Long, positionMs: Long) {
        viewModelScope.launch {
            val currentSpeed = _playbackSpeed.value
            val currentOffset = _subtitleOffsetMs.value
            val history = historyDao.getHistoryForVideo(videoId)
            val updatedHistory = if (history != null) {
                history.copy(positionMs = positionMs, speed = currentSpeed, subtitleOffsetMs = currentOffset, lastPlayedTime = System.currentTimeMillis())
            } else {
                PlaybackHistory(videoId, positionMs, currentSpeed, currentOffset)
            }
            historyDao.insertOrUpdateHistory(updatedHistory)

            videoRepository.getVideoById(videoId)?.let { video ->
                videoRepository.updateVideo(video.copy(lastPlayedPosition = positionMs))
            }
        }
    }

    fun deleteVideo(video: VideoFile) {
        viewModelScope.launch {
            if (_selectedVideo.value?.id == video.id) {
                _selectedVideo.value = null
                _currentSubtitles.value = emptyList()
                _activeSubtitle.value = null
            }
            videoRepository.deleteVideo(video)
            subtitleRepository.deleteSubtitlesForVideo(video.id)
            historyDao.deleteHistoryForVideo(video.id)
        }
    }

    // --- Subtitle Track Sync ---
    fun updatePlayerTime(timeMs: Long) {
        val adjustedTime = timeMs + _subtitleOffsetMs.value
        val match = _currentSubtitles.value.firstOrNull {
            adjustedTime >= it.startTimeMs && adjustedTime <= it.endTimeMs
        }
        if (_activeSubtitle.value != match) {
            _activeSubtitle.value = match
        }
    }

    // --- Subtitle Properties Customization ---
    fun updateSubtitleStyle(
        fontSizeSp: Float = _subtitleStyle.value.fontSizeSp,
        fontFamily: String = _subtitleStyle.value.fontFamily,
        textColorHex: String = _subtitleStyle.value.textColorHex,
        backgroundColorHex: String = _subtitleStyle.value.backgroundColorHex,
        backgroundOpacity: Float = _subtitleStyle.value.backgroundOpacity,
        position: SubtitlePosition = _subtitleStyle.value.position
    ) {
        _subtitleStyle.value = SubtitleStyle(
            fontSizeSp = fontSizeSp,
            fontFamily = fontFamily,
            textColorHex = textColorHex,
            backgroundColorHex = backgroundColorHex,
            backgroundOpacity = backgroundOpacity,
            position = position
        )
        viewModelScope.launch {
            settingsDao.saveSettings(
                SubtitleSettings(
                    id = 1,
                    fontSize = fontSizeSp,
                    fontFamily = fontFamily,
                    fontColorHex = textColorHex,
                    backgroundColorHex = backgroundColorHex,
                    backgroundOpacity = backgroundOpacity,
                    position = position.name,
                    isBold = false,
                    themeMode = _themeMode.value
                )
            )
        }
    }

    fun updateThemeMode(theme: String) {
        _themeMode.value = theme
        viewModelScope.launch {
            val current = settingsDao.getSettings() ?: SubtitleSettings()
            settingsDao.saveSettings(current.copy(themeMode = theme))
        }
    }

    // --- Player Controller Values ---
    fun updatePlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        val video = _selectedVideo.value ?: return
        viewModelScope.launch {
            val history = historyDao.getHistoryForVideo(video.id) ?: PlaybackHistory(video.id, 0L, speed, 0L)
            historyDao.insertOrUpdateHistory(history.copy(speed = speed))
        }
    }

    fun updateSubtitleOffset(offsetMs: Long) {
        _subtitleOffsetMs.value = offsetMs
        val video = _selectedVideo.value ?: return
        viewModelScope.launch {
            val history = historyDao.getHistoryForVideo(video.id) ?: PlaybackHistory(video.id, 0L, _playbackSpeed.value, offsetMs)
            historyDao.insertOrUpdateHistory(history.copy(subtitleOffsetMs = offsetMs))
        }
    }

    fun toggleSubtitles(enabled: Boolean) {
        _subtitlesEnabled.value = enabled
    }

    // --- AI Subtitle Generator ---
    fun startTranscription(
        video: VideoFile,
        language: String,
        isOfflineMode: Boolean,
        enableNoiseReduction: Boolean,
        enableSpeakerId: Boolean
    ) {
        viewModelScope.launch {
            _isTranscribing.value = true
            _transcriptionProgress.value = 0
            _transcriptionStep.value = "Starting transcoder..."

            subtitleRepository.transcribeVideo(
                context = getApplication(),
                video = video,
                language = language,
                isOfflineMode = isOfflineMode,
                enableNoiseReduction = enableNoiseReduction,
                enableSpeakerId = enableSpeakerId
            ).collect { progress ->
                _transcriptionProgress.value = progress.percent
                _transcriptionStep.value = progress.status

                if (progress.isFinished) {
                    _isTranscribing.value = false
                    // Update video marker
                    videoRepository.updateVideo(video.copy(hasSubtitles = true))
                    // Reselect video to refresh subtitle flow content
                    selectVideo(video.copy(hasSubtitles = true))
                }
            }
        }
    }

    // --- Subtitle Editor Tools ---
    fun updateBlockText(block: SubtitleBlock, newText: String) {
        viewModelScope.launch {
            val updated = block.copy(text = newText)
            subtitleRepository.updateSubtitleBlock(updated)
        }
    }

    fun adjustBlockTiming(block: SubtitleBlock, shiftMs: Long) {
        viewModelScope.launch {
            val updated = block.copy(
                startTimeMs = (block.startTimeMs + shiftMs).coerceAtLeast(0L),
                endTimeMs = (block.endTimeMs + shiftMs).coerceAtLeast(100L)
            )
            subtitleRepository.updateSubtitleBlock(updated)
        }
    }

    fun mergeSubtitleBlocks(first: SubtitleBlock, second: SubtitleBlock) {
        viewModelScope.launch {
            val mergedText = "${first.text} / ${second.text}"
            val updated = first.copy(
                text = mergedText,
                endTimeMs = second.endTimeMs
            )
            subtitleRepository.updateSubtitleBlock(updated)
            subtitleRepository.deleteSubtitleBlock(second)
        }
    }

    fun splitSubtitleBlock(block: SubtitleBlock, splitTextIndex: Int) {
        if (splitTextIndex <= 0 || splitTextIndex >= block.text.length) return
        
        viewModelScope.launch {
            val firstText = block.text.substring(0, splitTextIndex).trim()
            val secondText = block.text.substring(splitTextIndex).trim()
            val midTime = block.startTimeMs + (block.durationMs / 2)

            val updatedFirst = block.copy(text = firstText, endTimeMs = midTime)
            subtitleRepository.updateSubtitleBlock(updatedFirst)

            val insertedSecond = SubtitleBlock(
                videoId = block.videoId,
                text = secondText,
                startTimeMs = midTime + 50,
                endTimeMs = block.endTimeMs,
                speaker = block.speaker,
                index = block.index + 1
            )
            subtitleRepository.insertSubtitleBlock(insertedSecond)
        }
    }

    fun deleteBlock(block: SubtitleBlock) {
        viewModelScope.launch {
            subtitleRepository.deleteSubtitleBlock(block)
        }
    }

    fun createNewBlock(videoId: Long, text: String, startMs: Long, endMs: Long, index: Int) {
        viewModelScope.launch {
            val newBlock = SubtitleBlock(
                videoId = videoId,
                text = text,
                startTimeMs = startMs,
                endTimeMs = endMs,
                index = index
            )
            subtitleRepository.insertSubtitleBlock(newBlock)
        }
    }

    // --- Advanced Local LLM AI Actions ---

    fun translateSubtitlesWithGemini(targetLang: String) {
        val video = _selectedVideo.value ?: return
        val subtitles = _currentSubtitles.value
        if (subtitles.isEmpty()) return

        viewModelScope.launch {
            _isProcessingAI.value = true
            _aiResultText.value = null
            
            val response = LocalLLMEngine.translateSubtitles(getApplication(), subtitles, targetLang)
            
            subtitleRepository.deleteSubtitlesForVideo(video.id)
            subtitleRepository.insertSubtitles(response)
            
            _aiResultText.value = "Successfully translated track into $targetLang!"
            _isProcessingAI.value = false
        }
    }

    private suspend fun parseAndCacheTranslatedSrt(videoId: Long, srtRaw: String) {
        try {
            val blocks = mutableListOf<SubtitleBlock>()
            val lines = srtRaw.split("\n")
            var currentIndex = 1
            var lineState = 0 // 0: Index, 1: Timing, 2: Text
            var startTime = 0L
            var endTime = 0L
            val textBuilder = StringBuilder()

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) {
                    if (textBuilder.isNotEmpty()) {
                        blocks.add(
                            SubtitleBlock(
                                videoId = videoId,
                                text = textBuilder.toString().trim(),
                                startTimeMs = startTime,
                                endTimeMs = endTime,
                                index = currentIndex++
                            )
                        )
                        textBuilder.clear()
                    }
                    lineState = 0
                    continue
                }

                when (lineState) {
                    0 -> {
                        if (trimmed.toIntOrNull() != null) {
                            lineState = 1
                        }
                    }
                    1 -> {
                        if (trimmed.contains("-->")) {
                            val times = trimmed.split("-->")
                            if (times.size == 2) {
                                startTime = parseSrtTimestamp(times[0].trim())
                                endTime = parseSrtTimestamp(times[1].trim())
                            }
                            lineState = 2
                        }
                    }
                    2 -> {
                        textBuilder.append(trimmed).append(" ")
                    }
                }
            }

            if (textBuilder.isNotEmpty()) {
                blocks.add(
                    SubtitleBlock(
                        videoId = videoId,
                        text = textBuilder.toString().trim(),
                        startTimeMs = startTime,
                        endTimeMs = endTime,
                        index = currentIndex
                    )
                )
            }

            if (blocks.isNotEmpty()) {
                subtitleRepository.deleteSubtitlesForVideo(videoId)
                subtitleRepository.insertSubtitles(blocks)
            }
        } catch (e: Exception) {
            Log.e("ViewModel", "SRT parsing failed", e)
        }
    }

    private fun parseSrtTimestamp(time: String): Long {
        return try {
            val parts = time.split(",")
            val ms = parts.getOrNull(1)?.toLongOrNull() ?: 0L
            val subparts = parts[0].split(":")
            val hr = subparts.getOrNull(0)?.toLongOrNull() ?: 0L
            val min = subparts.getOrNull(1)?.toLongOrNull() ?: 0L
            val sec = subparts.getOrNull(2)?.toLongOrNull() ?: 0L
            (hr * 3600000L) + (min * 60000L) + (sec * 1000L) + ms
        } catch (e: Exception) {
            0L
        }
    }

    fun summarizeSpeechTranscript() {
        val subtitles = _currentSubtitles.value
        if (subtitles.isEmpty()) return

        viewModelScope.launch {
            _isProcessingAI.value = true
            _aiResultText.value = null
            
            val response = LocalLLMEngine.generateSummary(subtitles)
            
            _aiResultText.value = response
            _isProcessingAI.value = false

            // save to VideoFile metadata
            _selectedVideo.value?.let { video ->
                val updated = video.copy(summary = response)
                videoRepository.updateVideo(updated)
                _selectedVideo.value = updated
            }
        }
    }

    fun extractKeywordsFromTranscript() {
        val subtitles = _currentSubtitles.value
        if (subtitles.isEmpty()) return

        viewModelScope.launch {
            _isProcessingAI.value = true
            _aiResultText.value = null
            
            val keywords = LocalLLMEngine.extractKeywords(subtitles)
            
            _aiResultText.value = if (keywords.isEmpty()) "No tags extracted." else "Keywords extracted:\n\n" + keywords.joinToString(", ")
            _isProcessingAI.value = false

            // save to database
            _selectedVideo.value?.let { video ->
                val updated = video.copy(keywords = keywords)
                videoRepository.updateVideo(updated)
                _selectedVideo.value = updated
            }
        }
    }

    fun generateChaptersFromTranscript() {
        val subtitles = _currentSubtitles.value
        if (subtitles.isEmpty()) return

        viewModelScope.launch {
            _isProcessingAI.value = true
            _aiResultText.value = null

            val response = LocalLLMEngine.generateChapters(subtitles)
            
            _aiResultText.value = response
            _isProcessingAI.value = false

            if (response.isNotEmpty()) {
                // Parse chapters to ChapterMarker list
                val markers = parseChapterMarkers(response)
                _selectedVideo.value?.let { video ->
                    val updated = video.copy(chapters = markers)
                    videoRepository.updateVideo(updated)
                    _selectedVideo.value = updated
                }
            }
        }
    }

    private fun parseChapterMarkers(rawText: String): List<ChapterMarker> {
        val list = mutableListOf<ChapterMarker>()
        val lines = rawText.split("\n")
        lines.forEach { line ->
            try {
                val trimmed = line.trim()
                // Format: HH:MM:SS - Title or MM:SS - Title
                val parts = trimmed.split("-", limit = 2)
                if (parts.size == 2) {
                    val timeStr = parts[0].trim()
                    val title = parts[1].trim()
                    val timeMs = parseTimeStringToMs(timeStr)
                    list.add(ChapterMarker(title, timeMs))
                }
            } catch (e: Exception) {
                // skip corrupt entry
            }
        }
        return list
    }

    private fun parseTimeStringToMs(timeStr: String): Long {
        return try {
            val parts = timeStr.split(":")
            if (parts.size == 3) {
                val hr = parts[0].toLong()
                val min = parts[1].toLong()
                val sec = parts[2].toLong()
                (hr * 3600000L) + (min * 60000L) + (sec * 1000L)
            } else if (parts.size == 2) {
                val min = parts[0].toLong()
                val sec = parts[1].toLong()
                (min * 60000L) + (sec * 1000L)
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    fun dismissAiDialog() {
        _aiResultText.value = null
    }

    // --- Export Utilitaries ---
    fun getSrtContent(): String {
        return subtitleRepository.convertToSrt(_currentSubtitles.value)
    }

    fun getVttContent(): String {
        return subtitleRepository.convertToVtt(_currentSubtitles.value)
    }

    fun getTxtContent(): String {
        return subtitleRepository.convertToTxt(_currentSubtitles.value)
    }
}

// Custom ViewModel Factory keeping app extremely robust
class SubtitlePlayerViewModelFactory(
    private val application: Application,
    private val videoRepository: VideoRepository,
    private val subtitleRepository: SubtitleRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SubtitlePlayerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SubtitlePlayerViewModel(application, videoRepository, subtitleRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
