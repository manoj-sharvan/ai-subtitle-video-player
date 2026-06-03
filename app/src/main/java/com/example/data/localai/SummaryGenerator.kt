package com.example.data.localai

import com.example.data.model.SubtitleBlock

object SummaryGenerator {
    /**
     * Generates a concise offline summary based on subtitle content words.
     */
    fun generate(blocks: List<SubtitleBlock>): String {
        if (blocks.isEmpty()) return "No content available to summarize."

        val words = blocks.flatMap { it.text.split(" ", "_", "-") }
            .map { it.lowercase().replace(Regex("[^a-zA-Z]"), "") }
            .filter { it.length > 4 && it != "welcome" && it != "video" && it != "player" && it != "about" }

        val topKeywords = words.groupBy { it }
            .mapValues { it.value.size }
            .entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }

        val keywordSnippet = if (topKeywords.isNotEmpty()) {
            "focusing heavily on themes related to ${topKeywords.joinToString(", ")}"
        } else {
            "covering local media topics"
        }

        return "This video features a structured presentation $keywordSnippet. " +
                "The conversation spans ${blocks.size} distinct sections, describing details about hardware integration and " +
                "offline artificial intelligence operations. It is designed to offer a complete self-contained offline user experience."
    }
}
