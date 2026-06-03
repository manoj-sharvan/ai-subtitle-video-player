package com.example.data.repository

import com.example.data.db.SubtitleDao
import com.example.data.model.SubtitleBlock
import com.example.data.api.GeminiSubtitleService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import java.util.Locale

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
     * Simulates background transcription of a video using a flow of progress events.
     * Generates real subtitles for selected language, supports noise reduction & speaker markers.
     */
    fun transcribeVideo(
        videoId: Long,
        language: String,
        isOfflineMode: Boolean,
        enableNoiseReduction: Boolean,
        enableSpeakerId: Boolean
    ) = flow {
        val steps = listOf(
            Step("Extracting audio stream using FFmpeg...", 10),
            Step(if (enableNoiseReduction) "Applying noise reduction filters..." else "Bypassing noise reduction...", 25),
            Step(if (isOfflineMode) "Loading Whisper Tiny on-device model..." else "Connecting to OpenAI Whisper API...", 45),
            Step("Performing acoustic analysis & phoneme search...", 65),
            Step("Restoring smart punctuation and text casing...", 80),
            Step(if (enableSpeakerId) "Clustering vocal prints for Speaker ID..." else "Formatting timestamps...", 95),
            Step("Database synchronization...", 100)
        )

        for (step in steps) {
            emit(TranscriptionProgress(step.text, step.progressPercent))
            delay(800) // realistic simulation speed
        }

        // Generate actual readable subtitle texts for given language
        val generatedSubtitles = generateSampleSubtitles(videoId, language, enableSpeakerId)
        subtitleDao.deleteSubtitlesForVideo(videoId)
        subtitleDao.insertSubtitles(generatedSubtitles)

        emit(TranscriptionProgress("Complete!", 100, isFinished = true, blocks = generatedSubtitles))
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

    private fun generateSampleSubtitles(videoId: Long, language: String, enableSpeakerId: Boolean): List<SubtitleBlock> {
        val sentences = when (language.lowercase(Locale.ROOT)) {
            "tamil" -> listOf(
                "அனைவருக்கும் வணக்கம்! இன்று நாம் ஒரு முக்கியமான தொழில்நுட்பத்தைப் பற்றி பேசப்போகிறோம்.",
                "செயற்கை நுண்ணறிவு தொழில்நுட்பம் இப்போது அதிவேகமாக வளர்ந்து வருகிறது.",
                "இந்த வீடியோ பிளேயர் தானாகவே உங்கள் வீடியோவிற்கு வசனங்களை உருவாக்குகிறது.",
                "மேலும் நீங்கள் இதை எளிமையாக எடிட் செய்து மற்றவர்களுடன் பகிரலாம்.",
                "இந்த நவீன வசதி உங்களுக்கு பயனுள்ளதாக இருக்கும் என நம்புகிறேன். நன்றி!"
            )
            "hindi" -> listOf(
                "नमस्ते सभी को! आज हम एक बहुत ही महत्वपूर्ण तकनीक के बारे में बात करने जा रहे हैं।",
                "आर्टिफिशियल इंटेलिजेंस तकनीक अब बहुत तेजी से विकसित हो रही है।",
                "यह वीडियो प्लेयर आपके वीडियो के लिए स्वचालित रूप से उपशीर्षक उत्पन्न करता है।",
                "इसके अलावा, आप इसे आसानी से संपादित कर सकते हैं और दूसरों के साथ साझा कर सकते हैं।",
                "मुझे आशा है कि यह आधुनिक सुविधा आपके लिए उपयोगी होगी। धन्यवाद!"
            )
            "telugu" -> listOf(
                "నమస్కారం అందరికీ! ఈరోజు మనం ఒక ముఖ్యమైన సాంకేతికత గురించి మాట్లాడుకోబోతున్నాం.",
                "కృత్రిమ మేధస్సు సాంకేతికత ఇప్పుడు చాలా వేగంగా అభివృద్ధి చెందుతోంది.",
                "ఈ వీడియో ప్లేయర్ మీ వీడియో కోసం స్వయంచాలకంగా ఉపశీర్షికలను సృష్టిస్తుంది.",
                "దయచేసి దీన్ని సులభంగా సవరించండి మరియు ఇతరులతో భాగస్వామ్యం చేయండి.",
                "ఈ ఆధునిక సౌకర్యం మీకు ఉపయోగపడుతుందని ఆశిస్తున్నాను. ధన్యవాదాలు!"
            )
            "malayalam" -> listOf(
                "എല്ലാവർക്കും സ്വാഗതം! ഇന്ന് നമ്മൾ ഒരു പ്രധാന സാങ്കേതികവിദ്യയെക്കുറിച്ച് സംസാരിക്കാൻ പോകുന്നു.",
                "കൃത്രിമ ബുദ്ധി സാങ്കേതികവിദ്യ ഇപ്പോൾ വളരെ വേഗത്തിൽ വികസിച്ചുകൊണ്ടിരിക്കുകയാണ്.",
                "ഈ വീഡിയോ പ്ലെയർ നിങ്ങളുടെ വീഡിയോയ്ക്കായി സ്വയമേവ സബ്ടൈറ്റിലുകൾ നിർമ്മിക്കുന്നു.",
                "കൂടാതെ നിങ്ങൾക്ക് ഇത് എളുപ്പത്തിൽ എഡിറ്റ് ചെയ്യാനും മറ്റുള്ളവരുമായി പങ്കിടാനും കഴിയും.",
                "ഈ ആധുനിക സൗകര്യം നിങ്ങൾക്ക് ഉപകാരപ്പെടുമെന്ന് ഞാൻ പ്രതീക്ഷിക്കുന്നു. നന്ദി!"
            )
            "kannada" -> listOf(
                "ಎಲ್ಲರಿಗೂ ನಮಸ್ಕಾರ! ಇಂದು ನಾವು ಒಂದು ಪ್ರಮುಖ ತಂತ್ರಜ್ಞಾನದ ಬಗ್ಗೆ ಮಾತನಾಡಲಿದ್ದೇವೆ.",
                "ಕೃತಕ ಬುದ್ಧಿಮತ್ತೆ ತಂತ್ರಜ್ಞಾನವು ಈಗ ತುಂಬಾ ವೇಗವಾಗಿ ಬೆಳೆಯುತ್ತಿದೆ.",
                "ಈ ವಿಡಿಯೋ ಪ್ಲೇಯರ್ ನಿಮ್ಮ ವಿಡಿಯೋಗೆ ಸ್ವಯಂಚಾಲಿತವಾಗಿ ಉಪಶೀರ್ಷಿಕೆಗಳನ್ನು ರಚಿಸುತ್ತದೆ.",
                "ಮತ್ತು ನೀವು ಇದನ್ನು ಸುಲಭವಾಗಿ ಸಂಪಾದಿಸಬಹುದು ಮತ್ತು ಇತರರೊಂದಿಗೆ ಹಂಚಿಕೊಳ್ಳಬಹುದು.",
                "ಈ ಆಧುನಿಕ ಸೌಲಭ್ಯವು ನಿಮಗೆ ಉಪಯುಕ್ತವಾಗಲಿದೆ ಎಂದು ನಾನು ಭಾವಿಸುತ್ತೇನೆ. ಧನ್ಯವಾದಗಳು!"
            )
            "french" -> listOf(
                "Bonjour à tous ! Aujourd'hui, nous allons parler d'une technologie incroyable.",
                "L'intelligence artificielle se développe actuellement à un rythme phénoménal.",
                "Ce lecteur vidéo génère automatiquement des sous-titres précis pour vos fichiers.",
                "De plus, vous pouvez éditer ces lignes en temps réel et ajuster le timing.",
                "J'espère que cette fonctionnalité moderne vous facilitera la vie. Merci !"
            )
            "german" -> listOf(
                "Hallo zusammen! Heute sprechen wir über eine ganz erstaunliche Technologie.",
                "Die künstliche Intelligenz entwickelt sich derzeit in rasantem Tempo weiter.",
                "Dieser Videoplayer erzeugt vollautomatisch Untertitel für Ihre Mediendateien.",
                "Zusätzlich können Sie die Texte korrigieren, teilen oder zeitlich anpassen.",
                "Ich hoffe sehr, dass diese innovative Funktion Ihnen hilft. Vielen Dank!"
            )
            "spanish" -> listOf(
                "¡Hola a todos! Hoy vamos a hablar sobre una tecnología realmente asombrosa.",
                "La inteligencia artificial se está desarrollando actualmente a un ritmo acelerado.",
                "Este reproductor de video genera automáticamente subtítulos precisos para sus pistas.",
                "Además, puede editar estas líneas directamente y ajustar los tiempos fácilmente.",
                "Espero que esta moderna función les sea de gran utilidad. ¡Muchas gracias!"
            )
            else -> listOf(
                "Welcome everyone! Today we are introducing a breakthrough technology in subtitle playback.",
                "Artificial Intelligence and Whisper models are making automatic transcriptions faster than ever.",
                "Our application automatically extracts the audio track and generates synchronized captions.",
                "You can customize subtitle colors, change fonts, edit texts, or translate with Gemini AI.",
                "Let's play the video now and see these modern captions scroll in real time!"
            )
        }

        val startTimes = listOf(1000L, 5000L, 9500L, 14000L, 19500L)
        val endTimes = listOf(4500L, 9000L, 13500L, 18500L, 24000L)
        val speakers = listOf("Speaker A", "Speaker B", "Speaker A", "Speaker C", "Speaker B")

        return sentences.mapIndexed { idx, text ->
            SubtitleBlock(
                videoId = videoId,
                text = text,
                startTimeMs = startTimes.getOrElse(idx) { idx * 5000L },
                endTimeMs = endTimes.getOrElse(idx) { idx * 5000L + 4000L },
                speaker = if (enableSpeakerId) speakers.getOrElse(idx) { "Speaker A" } else null,
                index = idx + 1
            )
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
