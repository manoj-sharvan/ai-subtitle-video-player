package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiSubtitleService {
    private const val TAG = "GeminiSubtitleService"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun getApiKey(): String {
        return try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }
    }

    fun isApiKeyAvailable(): Boolean {
        val key = getApiKey()
        return key.isNotEmpty() && key != "MY_GEMINI_API_KEY"
    }

    /**
     * Send direct REST call to Gemini 3.5 Flash using raw JSONObject configuration.
     * Guaranteed to compile on any system with zero serialization converter dependencies.
     */
    suspend fun processText(prompt: String, systemPrompt: String? = null): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (!isApiKeyAvailable()) {
            return@withContext "API Key not configured. Please add GEMINI_API_KEY inside AI Studio Secrets."
        }

        var attempt = 0
        val maxRetries = 3
        var delayMs = 2000L

        while (attempt < maxRetries) {
            try {
                attempt++
                val requestJson = JSONObject()
                val contentsArray = org.json.JSONArray()
                val contentObj = JSONObject()
                val partsArray = org.json.JSONArray()
                val partObj = JSONObject()
                partObj.put("text", prompt)
                partsArray.put(partObj)
                contentObj.put("parts", partsArray)
                contentsArray.put(contentObj)
                requestJson.put("contents", contentsArray)

                if (systemPrompt != null) {
                    val sysInstructionObj = JSONObject()
                    val sysPartsArray = org.json.JSONArray()
                    val sysPartObj = JSONObject()
                    sysPartObj.put("text", systemPrompt)
                    sysPartsArray.put(sysPartObj)
                    sysInstructionObj.put("parts", sysPartsArray)
                    requestJson.put("systemInstruction", sysInstructionObj)
                }

                val configObj = JSONObject()
                configObj.put("temperature", 0.4)
                requestJson.put("generationConfig", configObj)

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = requestJson.toString().toRequestBody(mediaType)
                
                val request = Request.Builder()
                    .url("$BASE_URL?key=$apiKey")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.code == 429) {
                        if (attempt >= maxRetries) {
                            return@withContext "Error: API quota limit exceeded (429)."
                        }
                    } else if (response.code >= 500) {
                        if (attempt >= maxRetries) {
                            return@withContext "Error: Server error (${response.code})."
                        }
                    } else if (!response.isSuccessful) {
                        return@withContext "Error: API request failed with code ${response.code}."
                    } else {
                        val bodyStr = response.body?.string() ?: return@withContext "Error: Empty response body"
                        val responseJson = JSONObject(bodyStr)
                        val candidates = responseJson.optJSONArray("candidates")
                        val firstCandidate = candidates?.optJSONObject(0)
                        val content = firstCandidate?.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        val firstPart = parts?.optJSONObject(0)
                        
                        return@withContext firstPart?.optString("text") ?: "No content text generated"
                    }
                }
            } catch (e: java.io.IOException) {
                Log.e(TAG, "Network connection error on attempt $attempt", e)
                if (attempt >= maxRetries) {
                    return@withContext "Error: Network connection failed. Please check your internet connection."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error on attempt $attempt", e)
                if (attempt >= maxRetries) {
                    return@withContext "Error: ${e.message}"
                }
            }
            
            // Wait with exponential backoff before retrying
            kotlinx.coroutines.delay(delayMs)
            delayMs *= 2
        }
        
        return@withContext "Error: Unknown AI processing failure."
    }

    /**
     * Translate existing subtitle tracks with timings synced
     */
    suspend fun translateSubtitles(subtitlesRaw: String, targetLang: String): String {
        val systemPrompt = "You are an expert subtitle translator. Translate the following subtitles carefully, preserving the timing parameters exactly (e.g. 00:01:23,400 --> 00:01:24,800). Output Only the translated SRT format."
        val prompt = "Translate this SRT file to $targetLang:\n\n$subtitlesRaw"
        return processText(prompt, systemPrompt)
    }

    /**
     * Analyze and extract chapters
     */
    suspend fun generateChapters(subtitlesRaw: String): String {
        val systemPrompt = "You are an AI video analyzer. Analyze the following subtitles and generate a list of 3-5 major chapter markers. For each, output in format: TimeStamp (HH:MM:SS) - Chapter Title. One chapter per line. Output nothing else."
        val prompt = "Analyze these subtitles and generate chapter markers:\n\n$subtitlesRaw"
        return processText(prompt, systemPrompt)
    }

    /**
     * Summarize subtitle transcripts
     */
    suspend fun generateSummary(subtitlesRaw: String): String {
        val systemPrompt = "Provide a concise summary (2-3 sentences) of the video content based on this subtitle transcript."
        val prompt = "Analyze this transcript and write a summary:\n\n$subtitlesRaw"
        return processText(prompt, systemPrompt)
    }

    /**
     * Extract keyword tags
     */
    suspend fun extractKeywords(subtitlesRaw: String): List<String> {
        val systemPrompt = "Extract 5-8 relevant keyword tags from this subtitle transcript. Return them as a comma-separated list. No numbering or extra text."
        val prompt = "Extract keywords for:\n\n$subtitlesRaw"
        val response = processText(prompt, systemPrompt)
        if (response.contains("Error:") || response.contains("API Key")) return emptyList()
        return response.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * Upload base64 encoded audio track to Gemini 3.5 Flash for speech-to-text transcription.
     * Returns raw SRT format from the model.
     */
    suspend fun transcribeAudio(audioBytes: ByteArray, mimeType: String, language: String): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (!isApiKeyAvailable()) {
            return@withContext "API Key not configured. Please add GEMINI_API_KEY inside AI Studio Secrets."
        }
        
        var attempt = 0
        val maxRetries = 3
        var delayMs = 2000L
        
        while (attempt < maxRetries) {
            try {
                attempt++
                val base64Data = android.util.Base64.encodeToString(audioBytes, android.util.Base64.NO_WRAP)
                
                val requestJson = JSONObject()
                val contentsArray = org.json.JSONArray()
                val contentObj = JSONObject()
                val partsArray = org.json.JSONArray()
                
                // Audio inline data part
                val audioPart = JSONObject()
                val inlineData = JSONObject()
                inlineData.put("mimeType", mimeType)
                inlineData.put("data", base64Data)
                audioPart.put("inlineData", inlineData)
                partsArray.put(audioPart)
                
                // Transcription prompt part
                val textPart = JSONObject()
                textPart.put("text", "Listen to this audio track and generate highly accurate subtitles in SRT format. " +
                    "Language requested: $language. Do not summarize, transcribe exactly what is spoken. " +
                    "Output ONLY the raw SRT format content. Do not include any other markdown formatting or introductory text.")
                partsArray.put(textPart)
                
                contentObj.put("parts", partsArray)
                contentsArray.put(contentObj)
                requestJson.put("contents", contentsArray)
                
                val configObj = JSONObject()
                configObj.put("temperature", 0.2)
                requestJson.put("generationConfig", configObj)
                
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = requestJson.toString().toRequestBody(mediaType)
                
                val request = Request.Builder()
                    .url("$BASE_URL?key=$apiKey")
                    .post(body)
                    .build()
                    
                client.newCall(request).execute().use { response ->
                    if (response.code == 429) {
                        if (attempt >= maxRetries) {
                            return@withContext "Error: API quota limit exceeded (429)."
                        }
                    } else if (response.code >= 500) {
                        if (attempt >= maxRetries) {
                            return@withContext "Error: Server error (${response.code})."
                        }
                    } else if (!response.isSuccessful) {
                        return@withContext "Error: API request failed with code ${response.code}."
                    } else {
                        val bodyStr = response.body?.string() ?: return@withContext "Error: Empty response body"
                        
                        val responseJson = JSONObject(bodyStr)
                        val candidates = responseJson.optJSONArray("candidates")
                        val firstCandidate = candidates?.optJSONObject(0)
                        val content = firstCandidate?.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        val firstPart = parts?.optJSONObject(0)
                        
                        return@withContext firstPart?.optString("text") ?: "No content text generated"
                    }
                }
            } catch (e: java.io.IOException) {
                Log.e(TAG, "Network connection error on attempt $attempt", e)
                if (attempt >= maxRetries) {
                    return@withContext "Error: Network connection failed. Please check your internet connection."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error on attempt $attempt", e)
                if (attempt >= maxRetries) {
                    return@withContext "Error: ${e.message}"
                }
            }
            
            // Wait with exponential backoff before retrying
            kotlinx.coroutines.delay(delayMs)
            delayMs *= 2
        }
        
        return@withContext "Error: Unknown transcription failure."
    }
}
