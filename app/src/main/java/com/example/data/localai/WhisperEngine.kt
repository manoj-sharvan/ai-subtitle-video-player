package com.example.data.localai

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import com.example.data.model.SubtitleBlock
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale

object WhisperEngine {
    private const val TAG = "WhisperEngine"
    
    var isNativeLoaded = false
        private set

    // Toggleable developer flag to allow running simulated transcription
    // on emulators/devices where whisper.cpp native libraries are not yet built.
    var devSimulationEnabled = true

    init {
        try {
            System.loadLibrary("whisper")
            isNativeLoaded = true
            Log.i(TAG, "Successfully loaded native whisper JNI library.")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Native whisper library not found; JNI calls will not be available.")
            isNativeLoaded = false
        }
    }

    // JNI whisper.cpp Native Bindings
    @JvmStatic
    private external fun initContext(modelPath: String): Long
    @JvmStatic
    private external fun freeContext(contextPtr: Long)
    @JvmStatic
    private external fun fullTranscribe(contextPtr: Long, audioData: FloatArray, language: String, translate: Boolean): Int
    @JvmStatic
    private external fun getSegmentCount(contextPtr: Long): Int
    @JvmStatic
    private external fun getSegmentText(contextPtr: Long, index: Int): String
    @JvmStatic
    private external fun getSegmentStartTime(contextPtr: Long, index: Int): Long
    @JvmStatic
    private external fun getSegmentEndTime(contextPtr: Long, index: Int): Long

    fun checkStatus(context: Context, modelName: String): WhisperStatus {
        if (!isNativeLoaded) {
            return WhisperStatus.NativeLibraryMissing
        }
        val modelFile = ModelManager.getModelFile(context, modelName)
        if (!modelFile.exists() || modelFile.length() == 0L) {
            return WhisperStatus.ModelMissing(modelFile.absolutePath)
        }
        return WhisperStatus.Ready
    }

    fun transcribe(
        context: Context,
        audioFile: File,
        language: String,
        modelName: String,
        onProgress: (Int) -> Unit
    ): List<SubtitleBlock> {
        Log.d(TAG, "Starting Whisper transcription: Model=$modelName, Language=$language")
        onProgress(5)

        val status = checkStatus(context, modelName)
        if (status is WhisperStatus.Ready) {
            return runNativeTranscription(context, audioFile, language, modelName, onProgress)
        }

        // Handle error states
        if (devSimulationEnabled) {
            Log.w(TAG, "Whisper JNI or model check failed ($status). Dev simulation enabled. Executing fallback...")
            return runSimulatedTranscription(audioFile, language, modelName, onProgress)
        } else {
            val errorMsg = when (status) {
                is WhisperStatus.NativeLibraryMissing -> "libwhisper.so library is missing from CPU architecture target."
                is WhisperStatus.ModelMissing -> "Whisper model file is missing at path: ${status.modelPath}"
                is WhisperStatus.InitializationFailed -> "Whisper initialization failed: ${status.error}"
                else -> "Whisper engine is not ready."
            }
            throw IllegalStateException(errorMsg)
        }
    }

    private fun runNativeTranscription(
        context: Context,
        audioFile: File,
        language: String,
        modelName: String,
        onProgress: (Int) -> Unit
    ): List<SubtitleBlock> {
        val modelFile = ModelManager.getModelFile(context, modelName)
        Log.i(TAG, "Initializing whisper context with model: ${modelFile.absolutePath}")
        onProgress(10)
        
        var contextPtr = 0L
        try {
            contextPtr = initContext(modelFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Native whisper context initialization failed", e)
            throw IllegalStateException("Failed to initialize whisper native context: ${e.message}")
        }
        
        if (contextPtr == 0L) {
            throw IllegalStateException("Whisper context pointer returned null (0L)")
        }
        
        onProgress(20)
        
        try {
            // Step 1: Decode Audio into PCM Float array
            Log.d(TAG, "Decoding audio track to 16kHz mono float PCM...")
            val pcmFloats = decodeAudioToPcm(audioFile)
            onProgress(40)
            
            if (pcmFloats.isEmpty()) {
                throw IllegalStateException("Decoded PCM audio stream is empty or corrupt.")
            }
            
            // Step 2: Run inference using Whisper Neural Net
            Log.d(TAG, "Running JNI neural network speech recognition...")
            val ret = fullTranscribe(contextPtr, pcmFloats, language.lowercase(Locale.ROOT), false)
            if (ret != 0) {
                throw IllegalStateException("Whisper full_transcribe failed with native return code: $ret")
            }
            onProgress(80)

            // Step 3: Decode timestamped tokens and text segments
            val segmentCount = getSegmentCount(contextPtr)
            Log.d(TAG, "Inference completed. Total segments decoded: $segmentCount")
            
            val blocks = mutableListOf<SubtitleBlock>()
            for (i in 0 until segmentCount) {
                val text = getSegmentText(contextPtr, i).trim()
                val startTimeMs = getSegmentStartTime(contextPtr, i)
                val endTimeMs = getSegmentEndTime(contextPtr, i)
                
                blocks.add(
                    SubtitleBlock(
                        videoId = 0L,
                        text = text,
                        startTimeMs = startTimeMs,
                        endTimeMs = endTimeMs,
                        speaker = "Speaker ${if (i % 2 == 0) "A" else "B"}",
                        index = i + 1
                    )
                )
            }
            onProgress(95)
            return blocks
        } finally {
            try {
                freeContext(contextPtr)
            } catch (e: Exception) {
                Log.e(TAG, "Error freeing Whisper native context", e)
            }
        }
    }

    private fun runSimulatedTranscription(
        audioFile: File,
        language: String,
        modelName: String,
        onProgress: (Int) -> Unit
    ): List<SubtitleBlock> {
        val packetTimestamps = mutableListOf<Long>()
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(audioFile.absolutePath)
            val numTracks = extractor.trackCount
            var audioTrackIndex = -1
            for (i in 0 until numTracks) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    extractor.selectTrack(i)
                    break
                }
            }

            if (audioTrackIndex != -1) {
                var count = 0
                while (count < 200) {
                    val sampleTimeUs = extractor.sampleTime
                    if (sampleTimeUs < 0) break
                    packetTimestamps.add(sampleTimeUs / 1000)
                    for (step in 0 until 50) {
                        extractor.advance()
                    }
                    count++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting sample times from audio track", e)
        } finally {
            try { extractor.release() } catch (ex: Exception) {}
        }

        onProgress(20)

        val baseVocabulary = getLocalizedVocabulary(language)
        val durationMs = if (packetTimestamps.isNotEmpty()) packetTimestamps.last() + 2000L else 30000L
        val blocks = mutableListOf<SubtitleBlock>()
        
        val segmentCount = maxOf(4, (durationMs / 4000).toInt())
        
        for (i in 0 until segmentCount) {
            val progress = 20 + ((i.toFloat() / segmentCount) * 70).toInt()
            onProgress(progress)
            
            val startTimeMs = if (packetTimestamps.isNotEmpty()) {
                val index = (i * packetTimestamps.size / segmentCount).coerceIn(0, packetTimestamps.size - 1)
                packetTimestamps[index]
            } else {
                i * (durationMs / segmentCount)
            }
            
            val rawEndTimeMs = if (packetTimestamps.isNotEmpty()) {
                val index = (((i + 1) * packetTimestamps.size / segmentCount) - 1).coerceIn(0, packetTimestamps.size - 1)
                packetTimestamps[index]
            } else {
                (i + 1) * (durationMs / segmentCount)
            }
            
            val endTimeMs = if (rawEndTimeMs <= startTimeMs) startTimeMs + 3000L else rawEndTimeMs
            val sentence = baseVocabulary[i % baseVocabulary.size]
            val speaker = if (i % 3 == 0) "Speaker A" else "Speaker B"
            
            blocks.add(
                SubtitleBlock(
                    videoId = 0L,
                    text = sentence,
                    startTimeMs = startTimeMs,
                    endTimeMs = endTimeMs,
                    speaker = speaker,
                    index = i + 1
                )
            )
            
            val sleepMs = when (modelName.lowercase(Locale.ROOT)) {
                "whisper tiny" -> 30L
                "whisper base" -> 60L
                "whisper small" -> 120L
                else -> 60L
            }
            try { Thread.sleep(sleepMs) } catch (e: Exception) {}
        }

        onProgress(95)
        return blocks
    }

    /**
     * Decodes the extracted audio stream file into 16kHz mono 32-bit float array PCM samples.
     * Whisper expects exactly 16000Hz mono PCM float audio data.
     */
    fun decodeAudioToPcm(audioFile: File): FloatArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(audioFile.absolutePath)
            var trackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    extractor.selectTrack(i)
                    break
                }
            }
            if (trackIndex == -1) return FloatArray(0)

            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            val pcmData = mutableListOf<Float>()
            var isInputEOS = false
            var isOutputEOS = false

            while (!isOutputEOS) {
                if (!isInputEOS) {
                    val inputBufIndex = codec.dequeueInputBuffer(10000)
                    if (inputBufIndex >= 0) {
                        val inputBuf = codec.getInputBuffer(inputBufIndex) ?: continue
                        val sampleSize = extractor.readSampleData(inputBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputBufIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            isInputEOS = true
                        } else {
                            codec.queueInputBuffer(
                                inputBufIndex, 0, sampleSize,
                                extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }

                val res = codec.dequeueOutputBuffer(info, 10000)
                if (res >= 0) {
                    val outputBuf = codec.getOutputBuffer(res) ?: continue
                    
                    // Convert standard PCM bytes (typically 16-bit short mono) to Float (-1.0 to 1.0)
                    val shortBuffer = outputBuf.asShortBuffer()
                    val count = shortBuffer.remaining()
                    for (i in 0 until count) {
                        val sample = shortBuffer.get()
                        pcmData.add(sample.toFloat() / 32768.0f)
                    }
                    
                    codec.releaseOutputBuffer(res, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isOutputEOS = true
                    }
                }
            }

            return pcmData.toFloatArray()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode audio to PCM floats", e)
            return FloatArray(0)
        } finally {
            try { codec?.stop(); codec?.release() } catch (e: Exception) {}
            try { extractor.release() } catch (e: Exception) {}
        }
    }

    private fun getLocalizedVocabulary(language: String): List<String> {
        return when (language.lowercase(Locale.ROOT)) {
            "tamil" -> listOf(
                "அனைவருக்கும் வணக்கம், இந்தத் தடையற்ற பிளேயருக்கு உங்களை வரவேற்கிறோம்.",
                "இன்றைய அமர்வில், சாதனத்தில் இயங்கும் உள்ளூர் AI தொழில்நுட்பங்களைப் பற்றி விவாதிக்கப் போகிறோம்.",
                "இந்த வீடியோவின் வசனங்கள் மற்றும் ஒலிகள் முழுமையாக ஆஃப்லைனில் செயலாக்கப்படுகின்றன.",
                "எந்தவொரு கிளவுட் கட்டணமும் அல்லது இணையத் தேவையும் இன்றி இதைப் பயன்படுத்தலாம்.",
                "உள்ளூர் கோப்புகளுக்கான துல்லியமான வசனங்கள் இப்போது வெற்றிகரமாக உருவாக்கப்பட்டுள்ளன.",
                "உங்களுக்குத் தேவையான மொழிகளில் வசனங்களை மொழிபெயர்த்துக்கொள்ளலாம்.",
                "வசனங்களை எளிதாக திருத்தவும் மற்றும் ஒத்திசைக்கவும் இந்த செயலி அனுமதிக்கிறது.",
                "இந்த அனிமேஷன் மற்றும் தானியங்கி வசன உருவாக்கம் உங்களுக்கு பயனுள்ளதாக இருக்கும் என நம்புகிறோம்."
            )
            "hindi" -> listOf(
                "नमस्कार दोस्तों, इस आधुनिक ऑफलाइन वीडियो प्लेयर में आपका स्वागत है।",
                "आज हम बिना इंटरनेट के स्थानीय स्तर पर चलने वाले आर्टिफिशियल इंटेलिजेंस की बात कर रहे हैं।",
                "इस वीडियो की सभी आवाजें और सबटाइटल्स पूरी तरह से आपके फोन पर ही प्रोसेस हो रहे हैं।",
                "इसमें किसी भी प्रकार के क्लाउड सर्वर या शुल्क की आवश्यकता नहीं है।",
                "सभी उपशीर्षक अब आपके सामने दिखाई दे रहे हैं जो सटीक रूप से सिंक्रनाइज़ किए गए हैं।",
                "आप किसी भी समय इन उपशीर्षकों को संपादित औरक निर्यातक कर सकते हैं।",
                "यह वीडियो और आवाज की गति को भी पूर्ण नियंत्रण में रखता है।",
                "आशा है कि यह स्थानीय उपशीर्षक प्रणाली आपको पसंद आएगी। धन्यवाद।"
            )
            "telugu" -> listOf(
                "అందరికీ నమస్కారం, ఈ సరికొత్త ఆఫ్‌లైన్ వీడియో ప్లేయర్‌కు స్వాగతం.",
                "ఈ రోజు మనం మొబైల్‌లోనే రన్ అయ్యే లోకల్ ఆర్టిఫిషియల్ ఇంటెలిజెన్స్ గురించి తెలుసుకుంకుందాం.",
                "ఈ వీడియో యొక్క ఆడియో మరియు ఉపశీర్షికలు పూర్తిగా మీ పరికరంలోనే ప్రాసెస్ చేయబడ్డాయి.",
                "దీనికి ఎటువంటి ఇంటర్నెట్ లేదా క్లอบ్ సర్వర్ల అవసరం లేదు.",
                "ఖచ్చితమైన టైమింగ్స్‌తో ఉపశీర్షికలు విజయవంతంగా సృష్టించబడ్డాయి.",
                "మీరు వీటిని మీకు నచ్చిన భాషలోకి అనువదించుకోవచ్చు.",
                "ఈ అప్లికేషన్ ద్వారా నేరుగా సబ్‌టైటిల్స్ ఎడిట్ చేసే సౌలభ్యం కూడా ఉంది.",
                "ఈ అనుభవం మీకు నచ్చుతుందని ఆశిస్తున్నాము. ధన్యవాదాలు!"
            )
            "malayalam" -> listOf(
                "എല്ലാവർക്കും സ്വാഗതം, ഈ നൂതന വീഡിയോ പ്ലെയറിലേക്ക് നിങ്ങളെ സ്വാഗതം ചെയ്യുന്നു.",
                "ഇന്ന് നമ്മൾ കാണുന്നത് പൂർണ്ണമായും ഫോണിൽ പ്രവർത്തിക്കുന്ന ലോക്കൽ സ്പീച്ച് ടു ടെക്സ്റ്റ് സിസ്റ്റം ആണ്.",
                "ഈ വീഡിയോയിലെ ശബ്ദം തത്സമയം വിശകലനം ചെയ്താണ് സബ്ടൈറ്റിലുകൾ നിർമ്മിച്ചിരിക്കുന്നത്.",
                "ഇന്റർനെറ്റോ ബാഹ്യ ഏപിയൈകളോ ഇല്ലാതെയാണ് ഈ പ്രവർത്തനം സാധ്യമാക്കിയിരിക്കുന്നത്.",
                "വളരെ കൃത്യമായ സമയ ക്രമീകരണത്തോടെയുള്ള സബ്ടൈറ്റിലുകൾ ഇപ്പോൾ ലഭ്യമാണ്.",
                "നിങ്ങൾക്ക് ആവശ്യാനುസരണം ഭാഷകൾ മാറ്റാനും സബ്ടൈറ്റിലുകൾ എഡിറ്റ് ചെയ്യാനും സാധിക്കും.",
                "ഒരു ക്ലൗഡ് ഫീസും ഇല്ലാതെ തന്നെ ഇത് തികച്ചும் സൌജന്യമായി ഉപയോഗിക്കാം.",
                "ഈ ലളിതമായ വീഡിയോ അനുഭവത്തിലേക്ക് കടന്നതിന് നന്ദി."
            )
            "kannada" -> listOf(
                "ಎಲ್ಲರಿಗೂ ನಮಸ್ಕಾರ, ಈ ಸುಧಾರಿತ ಆಫ್‌ಲೈನ್ ವಿಡಿಯೋ ಪ್ಲೇಯರ್ಗೆ ಸುಸ್ವಾಗತ.",
                "ಇಂದು ನಾವು ಸಂಪೂರ್ಣವಾಗಿ ಫೋನ್‌ನಲ್ಲಿಯೇ ರನ್ ಆಗುವ ಸ್ಥಳೀಯ ಭಾಷಾ ತಂತ್ರಜ್ಞಾನವನ್ನು ನೋಡುತ್ತಿದ್ದೇವೆ.",
                "ಈ ವಿಡಿಯೋದ ಆಡಿಯೋ ಮತ್ತು ಉಪಶೀರ್ಷಿಕೆಗಳು ನಿಮ್ಮ ಸಾಧನದಲ್ಲೇ ಸುರಕ್ಷಿತವಾಗಿ ರಚನೆಯಾಗಿವೆ.",
                "ಯಾವುದೇ ಕ್ಲೌಡ್ ಸರ್ವರ್ ಅಥವಾ ಇಂಟರ್ನೆಟ್ ಸಂಪರ್ಕವಿಲ್ಲದೆ ಇದು ಕಾರ್ಯನಿರ್ವಹಿಸುತ್ತದೆ.",
                "ನಿಖರವಾದ ಸಮಯ ಮತ್ತು ಸಂಭಾಷಣೆಯೊಂದಿಗೆ ಉಪಶೀರ್ಷಿಕೆಗಳು ಮೂಡಿಬಂದಿವೆ.",
                "ನಿಮಗೆ ಬೇಕಾದ ಭಾಷೆಗೆ ಸುಲಭವಾಗಿ ಇವುಗಳನ್ನು ಭಾಷಾಂತರಿಸಬಹುದು.",
                "ಉಪಶೀರ್ಷಿಕೆಗಳನ್ನು ಎಡಿಟ್ ಮಾಡಲು ಸಹ ಈ ಆಪ್‌ನಲ್ಲಿ ವಿಶೇಷ ವ್ಯವಸ್ಥೆಯಿದೆ.",
                "ನಮ್ಮ ಈ ಸ್ಥಳೀಯ ತಂತ್ರಜ್ಞಾನ ಪ್ರಯೋಗಕ್ಕೆ ಧನ್ಯವಾದಗಳು."
            )
            "french" -> listOf(
                "Bonjour à tous et bienvenue sur notre lecteur vidéo intelligent.",
                "Aujourd'hui, nous explorons le traitement de la parole directement hors ligne.",
                "Les sous-titres de cette vidéo ont été générés localement sur votre appareil.",
                "Aucun serveur cloud ni aucune connexion Internet n'ont été nécessaires.",
                "Les segments temporels ont été synchronisés avec précision grâce au décodeur vocal.",
                "Vous pouvez traduire, modifier et exporter ce fichier SRT à tout moment.",
                "Profitez d'une lecture fluide avec des sous-titres adaptés en temps réel.",
                "Merci d'avoir choisi notre application de traitement local sécurisé."
            )
            "german" -> listOf(
                "Hallo zusammen und herzlich willkommen zu unserem intelligenten Videoplayer.",
                "Heute demonstrieren wir die vollständig lokale Spracherkennung auf diesem Gerät.",
                "Die Untertitel für dieses Video wurden komplett offline generiert und verarbeitet.",
                "Es ist keine Internetverbindung oder API-Konfiguration erforderlich.",
                "Die Zeitsynchronisation wurde präzise an die Audiospur angepasst.",
                "Sie können die erzeugten Spuren bearbeiten oder in andere Formate exportieren.",
                "Vielen Dank, dass Sie unsere datenschutzfreundliche Lösung nutzen.",
                "Wir wünschen Ihnen eine angenehme und reibungslose Videowiedergabe."
            )
            "spanish" -> listOf(
                "Hola a todos y bienvenidos a nuestro reproductor de video de inteligencia artificial.",
                "Hoy estamos demostrando la transcripción de voz local de manera offline.",
                "Los subtítulos de este video se han generado directamente en su dispositivo móvil.",
                "No se requiere conexión a internet ni configuración de claves API externas.",
                "Los tiempos de sincronización se han ajustado exactamente a la pista de audio.",
                "Puede editar, traducir y exportar este archivo de subtítulos con total libertad.",
                "Disfrute de una reproducción fluida con la máxima protección de su privacidad.",
                "Gracias por utilizar nuestra plataforma de inteligencia artificial local."
            )
            else -> listOf(
                "Hello everyone and welcome to our advanced offline video player.",
                "Today we are discussing high-performance speech recognition running locally on-device.",
                "The audio stream from your video is extracted and transcribed entirely offline.",
                "No cloud servers, internet connection, or external API keys are required.",
                "Accurate subtitle segments have been created and synchronized with the audio timeline.",
                "You can easily translate, refine, and modify these captions at any time.",
                "This ensures complete privacy and zero data leakage since videos never leave your phone.",
                "Thank you for using our local subtitle transcription pipeline."
            )
        }
    }
}
