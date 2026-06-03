package com.example.data.localai

import com.example.data.localai.model.RagChunk
import com.example.data.model.SubtitleBlock

object LocalLLMEngine {

    /**
     * Cleans up punctuation, capitalization, and segments.
     */
    fun refineSubtitles(blocks: List<SubtitleBlock>): List<SubtitleBlock> {
        return SubtitleRefiner.refine(blocks)
    }

    /**
     * Translates subtitles offline.
     */
    fun translateSubtitles(blocks: List<SubtitleBlock>, targetLanguage: String): List<SubtitleBlock> {
        return TranslationEngine.translate(blocks, targetLanguage)
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
        // Extract top words that are nouns/concepts
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
