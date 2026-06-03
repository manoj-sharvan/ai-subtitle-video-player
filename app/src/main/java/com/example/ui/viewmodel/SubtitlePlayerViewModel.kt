package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.SubtitleApplication
import com.example.data.api.GeminiSubtitleService
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

class SubtitlePlayerViewModel(
    application: Application,
    private val videoRepository: VideoRepository,
    private val subtitleRepository: SubtitleRepository
) : AndroidViewModel(application) {

    // --- Video Library Streams ---
    val allVideos: StateFlow<List<VideoFile>> = videoRepository.allVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentVideos: StateFlow<List<VideoFile>> = videoRepository.recentVideos
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

    init {
        // Pre-populate database with popular trailers if database is empty
        viewModelScope.launch {
            videoRepository.allVideos.collect { videos ->
                if (videos.isEmpty()) {
                    prepopulateDatabase()
                }
            }
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

    // --- Library Actions ---
    fun selectVideo(video: VideoFile) {
        _selectedVideo.value = video
        _activeSubtitle.value = null
        _playbackSpeed.value = 1.0f
        
        // Fetch subtitles associated with this video
        viewModelScope.launch {
            subtitleRepository.getSubtitlesForVideo(video.id).collect { blocks ->
                _currentSubtitles.value = blocks
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
        }
    }

    // --- Subtitle Track Sync ---
    fun updatePlayerTime(timeMs: Long) {
        val matches = _currentSubtitles.value.filter {
            timeMs >= it.startTimeMs && timeMs <= it.endTimeMs
        }
        _activeSubtitle.value = matches.firstOrNull()
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
    }

    // --- Player Controller Values ---
    fun updatePlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
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
                videoId = video.id,
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


    // --- Advanced Gemini AI Content Interceptors ---

    fun translateSubtitlesWithGemini(targetLang: String) {
        val video = _selectedVideo.value ?: return
        val subtitles = _currentSubtitles.value
        if (subtitles.isEmpty()) return

        viewModelScope.launch {
            _isProcessingAI.value = true
            _aiResultText.value = null
            
            // Generate standard raw SRT for model context
            val rawSrt = subtitleRepository.convertToSrt(subtitles)
            val response = GeminiSubtitleService.translateSubtitles(rawSrt, targetLang)
            
            if (response.startsWith("Error:") || response.startsWith("API Key")) {
                _aiResultText.value = "Translation Failed: $response"
            } else {
                // Parse generated translation blocks back to DB
                parseAndCacheTranslatedSrt(video.id, response)
                _aiResultText.value = "Successfully translated track into $targetLang!"
            }
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
            
            val rawSrt = subtitleRepository.convertToSrt(subtitles)
            val response = GeminiSubtitleService.generateSummary(rawSrt)
            
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
            
            val rawSrt = subtitleRepository.convertToSrt(subtitles)
            val keywords = GeminiSubtitleService.extractKeywords(rawSrt)
            
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

            val rawSrt = subtitleRepository.convertToSrt(subtitles)
            val response = GeminiSubtitleService.generateChapters(rawSrt)
            
            _aiResultText.value = response
            _isProcessingAI.value = false

            if (!response.contains("Error:") && !response.contains("API Key")) {
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
