package com.example.data.localai

import android.content.Context
import android.util.Log
import com.example.data.localai.model.RagChunk
import com.example.data.model.SubtitleBlock
import java.io.File

object LocalLLMEngine {
    private const val TAG = "LocalLLMEngine"

    var isNativeLoaded = false
        private set

    // Toggleable developer flag to allow running simulated LLM inference
    // on emulators/devices where llama.cpp native libraries are not yet built.
    var devSimulationEnabled = true

    init {
        try {
            System.loadLibrary("llama")
            isNativeLoaded = true
            Log.i(TAG, "Successfully loaded native llama JNI library.")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Native llama library not found; JNI calls will not be available.")
            isNativeLoaded = false
        }
    }

    // JNI llama.cpp Native Bindings
    @JvmStatic
    private external fun loadModel(modelPath: String): Long
    @JvmStatic
    private external fun freeModel(modelPtr: Long)
    @JvmStatic
    private external fun initContext(modelPtr: Long, contextSize: Int): Long
    @JvmStatic
    private external fun freeContext(contextPtr: Long)
    @JvmStatic
    private external fun generateCompletion(contextPtr: Long, prompt: String, maxTokens: Int): String

    /**
     * Cleans up punctuation, capitalization, and segments.
     */
    fun refineSubtitles(blocks: List<SubtitleBlock>): List<SubtitleBlock> {
        Log.d(TAG, "Refining subtitles using Local LLM...")
        return SubtitleRefiner.refine(blocks)
    }

    /**
     * Translates subtitles offline using dynamic factory resolving.
     */
    fun translateSubtitles(context: Context, blocks: List<SubtitleBlock>, targetLanguage: String): List<SubtitleBlock> {
        val engine = TranslationEngineFactory.getEngine(context)
        return engine.translate(blocks, targetLanguage)
    }

    /**
     * Summarizes subtitles.
     */
    fun generateSummary(blocks: List<SubtitleBlock>): String {
        return SummaryGenerator.generate(blocks)
    }

    /**
     * Extracts keyword tags.
     */
    fun extractKeywords(blocks: List<SubtitleBlock>): List<String> {
        if (blocks.isEmpty()) return emptyList()
        val stopWords = setOf(
            "the", "and", "this", "that", "with", "from", "your", "welcome", "about", "video", "player"
        )
        return blocks.flatMap { it.text.split(" ", "_", "-") }
            .map { it.lowercase().replace(Regex("[^a-zA-Z]"), "") }
            .filter { it.length > 4 && !stopWords.contains(it) }
            .groupBy { it }
            .mapValues { it.value.size }
            .entries
            .sortedByDescending { it.value }
            .take(6)
            .map { it.key }
    }

    /**
     * Generates chapter markers.
     */
    fun generateChapters(blocks: List<SubtitleBlock>): String {
        return ChapterGenerator.generate(blocks)
    }

    /**
     * Synthesizes RAG answers.
     */
    fun answerQuestion(query: String, contextChunks: List<RagChunk>): String {
        return RagAnswerGenerator.answer(query, contextChunks)
    }
}
