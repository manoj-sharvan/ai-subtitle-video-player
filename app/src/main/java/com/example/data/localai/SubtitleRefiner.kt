package com.example.data.localai

import com.example.data.model.SubtitleBlock
import java.util.Locale

object SubtitleRefiner {
    private val fillerWords = setOf(
        "uh", "um", "ah", "like", "you know", "hmm", "er", "oh", "actually", "basically", "so yeah"
    )

    /**
     * Refines transcribed subtitle text blocks locally by fixing:
     * - Capitalization (start of sentences, pronouns)
     * - Punctuation (adds missing periods/commas)
     * - Filler word removal
     */
    fun refine(blocks: List<SubtitleBlock>): List<SubtitleBlock> {
        return blocks.map { block ->
            var text = block.text.trim()

            // 1. Remove filler words
            for (filler in fillerWords) {
                // Handle case-insensitive boundary matches
                val regex = Regex("(?i)\\b$filler\\b[,]?\\s*")
                text = text.replace(regex, "")
            }

            // Clean up double spaces or trailing/leading punctuation
            text = text.replace(Regex("\\s+"), " ").trim()

            if (text.isEmpty()) {
                text = block.text
            }

            // 2. Fix Capitalization (Start of sentences and standalone words like 'I')
            text = capitalizeSentences(text)

            // 3. Add punctuation at the end if missing
            if (text.isNotEmpty() && !text.endsWith(".") && !text.endsWith("?") && !text.endsWith("!") && !text.endsWith("]")) {
                text += "."
            }

            block.copy(text = text)
        }
    }

    private fun capitalizeSentences(input: String): String {
        if (input.isEmpty()) return input
        val sb = StringBuilder()
        var capitalizeNext = true

        for (i in input.indices) {
            val char = input[i]
            if (capitalizeNext && char.isLetter()) {
                sb.append(char.uppercaseChar())
                capitalizeNext = false
            } else {
                sb.append(char)
                if (char == '.' || char == '?' || char == '!') {
                    capitalizeNext = true
                }
            }
        }

        // Specifically capitalize "i" as a pronoun in English
        return sb.toString()
            .replace(Regex("\\bi\\b"), "I")
            .replace(Regex("\\bIM\\b"), "I'm")
            .replace(Regex("\\bIm\\b"), "I'm")
    }
}
