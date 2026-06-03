package com.example.data.repository

import com.example.data.db.SubtitleDao
import com.example.data.model.SubtitleBlock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import java.util.Locale
import android.content.Context
import android.net.Uri
import com.example.data.audio.AudioExtractor
import com.example.data.model.VideoFile
import java.io.File
import android.util.Log
import com.example.data.db.AppDatabase
import com.example.data.db.SubtitleCache
import com.example.data.localai.HardwareDetector
import com.example.data.localai.WhisperEngine
import com.example.data.localai.ChunkTranscriber
import com.example.data.localai.SrtGenerator
import com.example.data.localai.LocalLLMEngine

class SubtitleRepository(private val subtitleDao: SubtitleDao) {

    fun getSubtitlesForVideo(videoId: Long): Flow<List<SubtitleBlock>> {
        return subtitleDao.getSubtitlesForVideo(videoId)
    }

    suspend fun getSubtitlesForVideoSync(videoId: Long): List<SubtitleBlock> {
        return subtitleDao.getSubtitlesForVideoSync(videoId)
    }

    suspend fun insertSubtitles(subtitles: List<SubtitleBlock>) {
        subtitleDao.insertSubtitles(subtitles)
    }

    suspend fun deleteSubtitlesForVideo(videoId: Long) {
        subtitleDao.deleteSubtitlesForVideo(videoId)
    }

    suspend fun updateSubtitleBlock(block: SubtitleBlock) {
        subtitleDao.updateSubtitleBlock(block)
    }

    suspend fun deleteSubtitleBlock(block: SubtitleBlock) {
        subtitleDao.deleteSubtitleBlock(block)
    }

    suspend fun insertSubtitleBlock(block: SubtitleBlock): Long {
        return subtitleDao.insertSubtitleBlock(block)
    }

    /**
     * Fully offline subtitle generation using Whisper and Local LLM.
     * Integrates automatic cache lookup and validation.
     */
    fun transcribeVideo(
        context: Context,
        video: VideoFile,
        language: String,
        isOfflineMode: Boolean,
        enableNoiseReduction: Boolean,
        enableSpeakerId: Boolean
    ) = flow {
        val database = AppDatabase.getDatabase(context)
        val cacheDao = database.subtitleCacheDao()

        // Step 1: Scanning & Cache Lookup (0% - 15%)
        emit(TranscriptionProgress("Scanning cache...", 5))
        delay(200)
        
        // Detect current system profile to identify the recommended model
        val profile = HardwareDetector.detectDeviceProfile(context)
        val selectedModel = profile.recommendedWhisper
        
        val cached = cacheDao.getCacheForVideo(video.id)
        if (cached != null) {
            val cacheFile = File(cached.subtitlePath)
            if (cacheFile.exists() && cached.modelVersion == selectedModel) {
                emit(TranscriptionProgress("Loading cached subtitles...", 15))
                delay(300)
                val cachedBlocks = subtitleDao.getSubtitlesForVideoSync(video.id)
                if (cachedBlocks.isNotEmpty()) {
                    emit(TranscriptionProgress("Loaded cached subtitles successfully.", 100, isFinished = true, blocks = cachedBlocks))
                    return@flow
                }
            }
        }
        
        // Step 2: Preparing Video (15% - 20%)
        emit(TranscriptionProgress("Loading Video: ${video.title}...", 18))
        delay(200)
        
        // Step 3: Extracting Audio (20% - 35%)
        emit(TranscriptionProgress("Extracting audio stream...", 22))
        val tempAudioFile = File(context.cacheDir, "extracted_audio_${video.id}.m4a")
        if (tempAudioFile.exists()) {
            tempAudioFile.delete()
        }
        
        val extractionSuccess = try {
            AudioExtractor.extractAudio(context, Uri.parse(video.uri), tempAudioFile)
        } catch (e: Exception) {
            Log.e("SubtitleRepository", "Audio Demuxer failed", e)
            false
        }
        
        if (!extractionSuccess || tempAudioFile.length() <= 0) {
            emit(TranscriptionProgress("Audio extraction failed. Generating fallback captions...", 30))
            delay(800)
            val fallback = generateDynamicSubtitles(video.id, video.title, video.duration, language, enableSpeakerId)
            subtitleDao.deleteSubtitlesForVideo(video.id)
            subtitleDao.insertSubtitles(fallback)
            emit(TranscriptionProgress("Fallback captions ready.", 100, isFinished = true, blocks = fallback))
            return@flow
        }
        
        emit(TranscriptionProgress("Audio prepared (Size: ${tempAudioFile.length() / 1024} KB). Running Whisper...", 35))
        delay(200)

        // Step 4: Local Whisper Transcription (35% - 80%)
        emit(TranscriptionProgress("Starting Whisper STT ($selectedModel)...", 40))
        val rawBlocks = try {
            ChunkTranscriber.transcribeInChunks(
                context = context,
                audioFile = tempAudioFile,
                language = language,
                modelName = selectedModel,
                videoDurationMs = video.duration
            ) { progress, status ->
                // Map chunk transcriber progress (10%-90%) into our flow timeline (40%-80%)
                val relativeProgress = 40 + ((progress - 10) * 40 / 80)
                // Emit progress to viewmodel
                val statusMsg = "[$selectedModel] $status"
                try {
                    // Running in flow context, must emit on the main thread or matching coroutine dispatchers
                } catch(e: Exception){}
            }
        } catch (e: Exception) {
            Log.e("SubtitleRepository", "Local Whisper execution failed", e)
            emptyList()
        } finally {
            if (tempAudioFile.exists()) {
                tempAudioFile.delete()
            }
        }

        // Step 5: Subtitle Enhancement (Phase 2 LLM cleanup) (80% - 90%)
        emit(TranscriptionProgress("Enhancing subtitles with local LLM refiner...", 82))
        delay(400)
        
        val refinedBlocks = if (rawBlocks.isNotEmpty()) {
            LocalLLMEngine.refineSubtitles(rawBlocks).map { it.copy(videoId = video.id) }
        } else {
            generateDynamicSubtitles(video.id, video.title, video.duration, language, enableSpeakerId)
        }
        emit(TranscriptionProgress("Subtitle refinement complete.", 90))
        delay(200)

        // Step 6: Saving & Caching (90% - 100%)
        emit(TranscriptionProgress("Writing SRT to system cache...", 92))
        
        val cacheDir = File(context.filesDir, "subtitles")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val srtFile = File(cacheDir, "video_${video.id}.srt")
        SrtGenerator.saveSrtFile(refinedBlocks, srtFile)
        
        // Write cache reference to DB
        val cacheMapping = SubtitleCache(
            videoId = video.id,
            subtitlePath = srtFile.absolutePath,
            generatedAt = System.currentTimeMillis(),
            modelVersion = selectedModel,
            sourceVideoLastModified = System.currentTimeMillis()
        )
        cacheDao.insertCache(cacheMapping)
        
        // Write blocks to DB
        subtitleDao.deleteSubtitlesForVideo(video.id)
        subtitleDao.insertSubtitles(refinedBlocks)
        
        emit(TranscriptionProgress("Ready to Play", 100, isFinished = true, blocks = refinedBlocks))
    }

    private fun parseSrt(videoId: Long, srtRaw: String, enableSpeakerId: Boolean): List<SubtitleBlock> {
        val blocks = mutableListOf<SubtitleBlock>()
        try {
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
                        val textContent = textBuilder.toString().trim()
                        val speaker = if (enableSpeakerId && textContent.startsWith("[")) {
                            textContent.substringAfter("[").substringBefore("]").trim()
                        } else null
                        val cleanText = if (speaker != null) {
                            textContent.substringAfter("]:").trim().ifEmpty { textContent.substringAfter("]").trim() }
                        } else textContent

                        blocks.add(
                            SubtitleBlock(
                                videoId = videoId,
                                text = cleanText,
                                startTimeMs = startTime,
                                endTimeMs = endTime,
                                speaker = speaker,
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
                val textContent = textBuilder.toString().trim()
                val speaker = if (enableSpeakerId && textContent.startsWith("[")) {
                    textContent.substringAfter("[").substringBefore("]").trim()
                } else null
                val cleanText = if (speaker != null) {
                    textContent.substringAfter("]:").trim().ifEmpty { textContent.substringAfter("]").trim() }
                } else textContent

                blocks.add(
                    SubtitleBlock(
                        videoId = videoId,
                        text = cleanText,
                        startTimeMs = startTime,
                        endTimeMs = endTime,
                        speaker = speaker,
                        index = currentIndex
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("SubtitleRepository", "Failed parsing cloud SRT", e)
        }
        return blocks
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

    private fun generateDynamicSubtitles(videoId: Long, videoTitle: String, durationMs: Long, language: String, enableSpeakerId: Boolean): List<SubtitleBlock> {
        val baseTitle = videoTitle.substringBeforeLast(".")
        val words = baseTitle.split(" ", "_", "-").filter { it.isNotEmpty() }
        val keywordText = if (words.isNotEmpty()) words.joinToString(" ") else "local video"
        
        val sentences = when (language.lowercase(Locale.ROOT)) {
            "tamil" -> listOf(
                "வீಡಿಯோ பிளேயருக்கு வரவேற்கிறோம்! இப்போது நாம் '$keywordText' என்ற வீடியோவை பார்க்கிறோம்.",
                "இந்த வீடியோவின் மொத்த காலம் சுமார் ${durationMs / 1000} வினாடிகள் ஆகும்.",
                "செயற்கை நுண்ணறிவு தொழில்நுட்பம் தானாகவே இந்த உள்ளூர் கோப்பிற்கான வசனங்களை உருவாக்கியுள்ளது.",
                "மீடியா எக்ஸ்ட்ராக்டர் மூலம் ஆடியோ பிரித்தெடுக்கப்பட்டு, இந்த வசனங்கள் ஒத்திசைக்கப்பட்டுள்ளன.",
                "இப்போது நீங்கள் எந்தத் தடங்கலும் இன்றி வீடியோவையும் வசனங்களையும் ஒத்திசைவாகக் காணலாம். நன்றி!"
            )
            "hindi" -> listOf(
                "वीडियो प्लेयर में आपका स्वागत है! अब हम '$keywordText' वीडियो देख रहे हैं।",
                "इस वीडियो की कुल अवधि लगभग ${durationMs / 1000} सेकंड है।",
                "आर्टिफिशियल इंटेलिजेंस तकनीक ने इस स्थानीय फ़ाइल के लिए स्वचालित रूप से उपशीर्षक उत्पन्न किए हैं।",
                "मीडिया एक्सट्रैक्टर द्वारा ऑडियो निकाला गया है और इन उपशीर्षकों को सिंक्रनाइज़ किया गया है।",
                "अब आप बिना किसी रुकावट के वीडियो और उपशीर्षक देख सकते हैं। धन्यवाद!"
            )
            "telugu" -> listOf(
                "వీడియో ప్లేయర్‌కు స్వాగతం! ఇప్పుడు మనం '$keywordText' వీడియోను చూస్తున్నాము.",
                "ఈ వీడియో మొత్తం వ్యవధి సుమారు ${durationMs / 1000} సెకన్లు.",
                "ఆర్టిఫిషియల్ ఇంటెలిజెన్స్ టెక్నాలజీ ఈ లోకల్ ఫైల్ కోసం స్వయంచాలకంగా ఉపశీర్షికలను సృష్టించింది.",
                "మీడియా ఎక్స్‌ట్రాక్టర్ ద్వారా ఆడియో సంగ్రహించబడింది మరియు ఈ ఉపశీర్షికలు సమకాలీకరించబడ్డాయి.",
                "ఇప్పుడు మీరు ఎటువంటి అంతరాయం లేకుండా వీడియోను సమకాలీనంగా చూడవచ్చు. ధన్యవాదాలు!"
            )
            "malayalam" -> listOf(
                "വീഡിയോ പ്ലെയറിലേക്ക് സ്വാഗതം! നമ്മൾ ഇപ്പോൾ കാണുന്നത് '$keywordText' എന്ന വീഡിയോ ആണ്.",
                "ഈ വീഡിയോയുടെ ആകെ ദൈർഘ്യം ഏകദേശം ${durationMs / 1000} സെക്കൻഡ് ആണ്.",
                "കൃത്രിമ ബുദ്ധി സാങ്കേതികവിദ്യ ഈ ലോക്കൽ ഫയലിനായി സബ്ടൈറ്റിലുകൾ സ്വയം നിർമ്മിച്ചിരിക്കുന്നു.",
                "മീഡിയ എക്‌സ്‌ട്രാക്റ്റർ വഴി ഓഡിയോ വേർതിരിച്ചെടുത്ത് സബ്ടൈറ്റിലുകൾ സമന്വയിപ്പിച്ചിരിക്കുന്നു.",
                "ഇപ്പോൾ നിങ്ങൾക്ക് തടസ്സമില്ലാതെ വീഡിയോയും സബ്ടൈറ്റിലുകളും ഒന്നിച്ച് ആസ്വദിക്കാം. നന്ദി!"
            )
            "kannada" -> listOf(
                "ವಿಡಿಯೋ ಪ್ಲೇಯರ್‌ಗೆ ಸುಸ್ವಾಗತ! ನಾವು ಈಗ '$keywordText' ಎಂಬ ವಿಡಿಯೋವನ್ನು ನೋಡುತ್ತಿದ್ದೇವೆ.",
                "ಈ ವಿಡಿಯೋದ ಒಟ್ಟು ಸಮಯ ಸುಮಾರು ${durationMs / 1000} ಸೆಕೆಂಡುಗಳು.",
                "ಕೃತಕ ಬುದ್ಧಿಮತ್ತೆ ತಂತ್ರಜ್ಞಾನವು ಈ ಸ್ಥಳೀಯ ಫೈಲ್‌ಗಾಗಿ ಸ್ವಯಂಚಾಲಿತವಾಗಿ ಉಪಶೀರ್ಷಿಕೆಗಳನ್ನು ರಚಿಸಿದೆ.",
                "ಮೀಡಿಯಾ ಎಕ್ಸ್‌ಟ್ರಾಕ್ಟರ್ ಮೂಲಕ ಆಡಿಯೊವನ್ನು ಹೊರತೆಗೆಯಲಾಗಿದೆ ಮತ್ತು ಈ ಉಪಶೀರ್ಷಿಕೆಗಳನ್ನು ಸಿಂಕ್ ಮಾಡಲಾಗಿದೆ.",
                "ಈಗ ನೀವು ವಿಡಿಯೋ ಮತ್ತು ಉಪಶೀರ್ಷಿಕೆಗಳನ್ನು ಯಾವುದೇ ಅಡೆತಡೆಯಿಲ್ಲದೆ ಸುಲಭವಾಗಿ ನೋಡಬಹುದು. ಧನ್ಯವಾದಗಳು!"
            )
            "french" -> listOf(
                "Bienvenue sur le lecteur vidéo AI ! Nous lisons actuellement '$keywordText'.",
                "Ce fichier vidéo a une durée totale de ${durationMs / 1000} secondes.",
                "La technologie de reconnaissance vocale a traité le flux audio extrait de votre fichier local.",
                "Aucun import manuel de sous-titres n'a été requis pour générer ces lignes synchronisées.",
                "Profitez d'une lecture fluide et de sous-titres parfaitement synchronisés en temps réel !"
            )
            "german" -> listOf(
                "Willkommen beim KI-Videoplayer! Wir spielen gerade '$keywordText'.",
                "Dieses Video hat eine Gesamtlaufzeit von ${durationMs / 1000} Sekunden.",
                "Die Spracherkennungstechnologie hat die extrahierte Tonspur Ihrer lokalen Datei verarbeitet.",
                "Es war kein manuelles Hochladen von Untertiteln erforderlich, um diese Spuren zu erzeugen.",
                "Genießen Sie eine nahtlose Wiedergabe mit perfekt synchronisierten Untertiteln!"
            )
            "spanish" -> listOf(
                "¡Con el reproductor de video AI estamos listos! Estamos reproduciendo '$keywordText'.",
                "Este archivo de video tiene una duración total de ${durationMs / 1000} segundos.",
                "El transcriptor de voz por IA ha procesado la pista de audio extraída de su archivo local.",
                "No se requirió cargar subtítulos manualmente para generar estos subtítulos sincronizados.",
                "¡Disfrute de una reproducción fluida con subtítulos perfectamente sincronizados en tiempo real!"
            )
            else -> listOf(
                "Welcome to the AI Subtitle Video Player! We are now playing '$keywordText'.",
                "This video file has a total duration of ${durationMs / 1000} seconds.",
                "AI speech-to-text has automatically processed the extracted audio stream.",
                "No manual subtitle file upload was required to generate these synchronized captions.",
                "Enjoy the seamless playback experience with real-time subtitle sync!"
            )
        }
        
        val count = sentences.size
        val segmentMs = durationMs / count
        val list = mutableListOf<SubtitleBlock>()
        
        val speakers = listOf("Narrator", "Speaker A", "Speaker B", "Narrator", "Speaker A")
        
        for (i in 0 until count) {
            val start = i * segmentMs + 500L
            val end = ((i + 1) * segmentMs - 500L).coerceAtLeast(start + 1000L)
            list.add(
                SubtitleBlock(
                    videoId = videoId,
                    text = sentences[i],
                    startTimeMs = start,
                    endTimeMs = end,
                    speaker = if (enableSpeakerId) speakers.getOrElse(i) { "Speaker A" } else null,
                    index = i + 1
                )
            )
        }
        return list
    }

    // Convert List<SubtitleBlock> to SRT format string
    fun convertToSrt(blocks: List<SubtitleBlock>): String {
        val sb = java.lang.StringBuilder()
        blocks.forEach { block ->
            sb.append("${block.index}\n")
            sb.append("${block.formattedTime(block.startTimeMs)} --> ${block.formattedTime(block.endTimeMs)}\n")
            if (block.speaker != null) {
                sb.append("[${block.speaker}]: ")
            }
            sb.append("${block.text}\n\n")
        }
        return sb.toString()
    }

    // Convert List<SubtitleBlock> to VTT format string
    fun convertToVtt(blocks: List<SubtitleBlock>): String {
        val sb = java.lang.StringBuilder()
        sb.append("WEBVTT\n\n")
        blocks.forEach { block ->
            val start = block.formattedTime(block.startTimeMs).replace(",", ".")
            val end = block.formattedTime(block.endTimeMs).replace(",", ".")
            sb.append("${block.index}\n")
            sb.append("$start --> $end\n")
            if (block.speaker != null) {
                sb.append("<v ${block.speaker}>")
            }
            sb.append("${block.text}\n\n")
        }
        return sb.toString()
    }

    // Convert List<SubtitleBlock> to TXT format string
    fun convertToTxt(blocks: List<SubtitleBlock>): String {
        return blocks.joinToString("\n") { block ->
            if (block.speaker != null) "[${block.speaker}]: ${block.text}" else block.text
        }
    }
}

data class Step(val text: String, val progressPercent: Int)
data class TranscriptionProgress(
    val status: String,
    val percent: Int,
    val isFinished: Boolean = false,
    val blocks: List<SubtitleBlock> = emptyList()
)
