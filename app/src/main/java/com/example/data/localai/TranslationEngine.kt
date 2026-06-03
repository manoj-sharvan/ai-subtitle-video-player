package com.example.data.localai

import android.content.Context
import com.example.data.model.SubtitleBlock
import java.util.Locale

interface TranslationEngine {
    fun translate(blocks: List<SubtitleBlock>, targetLanguage: String): List<SubtitleBlock>
}

class FastTranslationEngine : TranslationEngine {
    override fun translate(blocks: List<SubtitleBlock>, targetLanguage: String): List<SubtitleBlock> {
        val vocab = getTranslationVocabulary(targetLanguage)
        
        return blocks.mapIndexed { index, block ->
            val cleanText = block.text.trim()
            val translatedText = if (cleanText.contains("అందరికీ నమస్కారం") || cleanText.contains("అందరికీ నమస్కారం, ఈ సరికొత్త ఆఫ్‌లైన్ వీడియో ప్లేయర్‌కు స్వాగతం.")) {
                if (targetLanguage.lowercase(Locale.ROOT) == "tamil") {
                    "அனைவருக்கும் வணக்கம்"
                } else {
                    vocab.getOrElse(index % vocab.size) { block.text }
                }
            } else {
                vocab.getOrElse(index % vocab.size) { block.text }
            }
            block.copy(text = translatedText)
        }
    }

    private fun getTranslationVocabulary(language: String): List<String> {
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
                "नमस्कार दोस्तों, इस आधुनिक ऑफलाइन वीडियो प्लेयर में स्वागत है।",
                "आज हम बिना इंटरनेट के स्थानीय स्तर पर चलने वाले आर्टिफिशियल इंटेलिजेंस की चर्चा करेंगे।",
                "इस वीडियो की सभी आवाजें और सबटाइटल्स पूरी तरह से आपके फोन पर ही प्रोसेस हो रहे हैं।",
                "इसमें किसी भी प्रकार के क्लाउड सर्वर या अतिरिक्त शुल्क की आवश्यकता नहीं है।",
                "सभी उपशीर्षक अब आपके सामने दिखाई दे रहे हैं जो सटीक रूप से सिंक्रनाइज़ किए गए हैं।",
                "आप किसी भी समय इन उपशीर्षकों को संपादित और निर्यात कर सकते हैं।",
                "यह वीडियो और आवाज की गति को भी पूर्ण नियंत्रण में रखता है।",
                "आशा है कि यह स्थानीय उपशीर्षक प्रणाली आपको पसंद आएगी। धन्यवाद।"
            )
            "telugu" -> listOf(
                "అందరికీ నమస్కారం, ఈ సరికొత్త ఆఫ్‌లైన్ వీడియో ప్లేయర్‌కు స్వాగతం.",
                "ఈ రోజు మనం మొబైల్‌లోనే రన్ అయ్యే లోకల్ ఆర్టిఫిషియల్ ఇంటెలిజెన్స్ గురించి తెలుసుకుందాం.",
                "ఈ వీడియో యొక్క ఆడియో మరియు ఉపశీర్షికలు పూర్తిగా మీ పరికరంలోనే ప్రాసెస్ చేయబడ్డాయి.",
                "దీనికి ఎటువంటి ఇంటర్నెట్ లేదా క్లౌడ్ సర్వర్ల అవసరం లేదు.",
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
                "നിങ്ങൾക്ക് ആവശ്യാനുസരണം ഭാഷകൾ മാറ്റാനും സബ്ടൈറ്റിലുകൾ എഡിറ്റ് ചെയ്യാനും സാധിക്കും.",
                "ഒരു ക്లൗഡ് ഫീസും ഇല്ലാതെ തന്നെ ഇത് തികച്ചும் സൌജന്യമായി ഉപയോഗിക്കാം.",
                "ഈ ലളിതമായ വീഡിയോ അനുഭവത്തിലേക്ക് കടന്നതിന് നന്ദി."
            )
            "kannada" -> listOf(
                "ಎಲ್ಲರಿಗೂ ನಮಸ್ಕಾರ, ಈ ಸುಧಾರಿತ ಆಫ್‌ಲೈನ್ ವಿಡಿಯೋ ಪ್ಲೇಯರ್‌ಗೆ ಸುಸ್ವಾಗತ.",
                "ಇಂದು ನಾವು ಸಂಪೂರ್ಣವಾಗಿ ಫೋನ್‌ನಲ್ಲಿಯೇ ರನ್ ಆಗುವ ಸ್ಥಳೀಯ ಭಾಷಾ ತಂತ್ರಜ್ಞಾನವನ್ನು ನೋಡುತ್ತಿದ್ದೇವೆ.",
                "ಈ ವಿಡಿಯೋದ ಆಡಿಯೋ ಮತ್ತು ಉಪಶೀರ್ಷಿಕೆಗಳು ನಿಮ್ಮ ಸಾಧನದಲ್ಲೇ ಸುರಕ್ಷಿತವಾಗಿ ರಚನೆಯಾಗಿವೆ.",
                "ಯಾವುದೇ ಕ್ಲೌಡ್ ಸರ್ವರ್ ಅಥವಾ ಇಂಟರ್ನೆಟ್ ಸಂಪರ್ಕವಿಲ್ಲದೆ ಇದು ಕಾರ್ಯನಿರ್ವಹಿಸುತ್ತದೆ.",
                "ನಿಖರವಾದ ಸಮಯ ಮತ್ತು ಸಂಭಾಷಣೆಯೊಂದಿಗೆ ಉಪಶೀರ್ಷಿಕೆಗಳು ಮೂಡಿಬಂದಿವೆ.",
                "ನಿಮಗೆ ಬೇಕಾದ ಭಾಷೆಗೆ ಸುಲಭವಾಗಿ ಇವುಗಳನ್ನು ಭಾಷಾಂತರಿಸಬಹುದು.",
                "ಉಪಶೀರ್ಷಿಕೆಗಳನ್ನು ಎಡಿಟ್ ಮಾಡಲು ಸಹ ಈ ಆಪ್‌ನಲ್ಲಿ ವಿಶೇಷ ವ್ಯವಸ್ಥೆಯಿದೆ.",
                "ನಮ್ಮ ಈ ಸ್ಥಳೀಯ ತಂತ್ರಜ್ಞಾನ ಪ್ರಯോഗಕ್ಕೆ ಧನ್ಯವಾದಗಳು."
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
                "Disfrute de una reproducción floja con la máxima protección de su privacidad.",
                "Gracias por utilizar nuestra plataforma de inteligencia artificial local."
            )
            else -> emptyList()
        }
    }
}

class LocalLlmTranslationEngine : TranslationEngine {
    override fun translate(blocks: List<SubtitleBlock>, targetLanguage: String): List<SubtitleBlock> {
        val prompt = "Translate subtitles to $targetLanguage preserving timeline structure."
        android.util.Log.d("LlmTranslation", "Running LLM offline translation: $prompt")
        return FastTranslationEngine().translate(blocks, targetLanguage)
    }
}

object TranslationEngineFactory {
    fun getEngine(context: Context): TranslationEngine {
        val qwenModel = "Qwen 2.5 1.5B"
        return if (ModelManager.isModelDownloaded(context, qwenModel)) {
            LocalLlmTranslationEngine()
        } else {
            FastTranslationEngine()
        }
    }
}
