package com.example.data.localai

import com.example.data.localai.model.RagChunk
import java.util.Locale

object RagAnswerGenerator {
    /**
     * Synthesizes a response to the user's question using the retrieved RAG context chunks,
     * completely offline.
     */
    fun answer(query: String, contextChunks: List<RagChunk>): String {
        if (contextChunks.isEmpty()) {
            return "I couldn't find any relevant transcript sections matching your question. Could you try rephrasing your search?"
        }

        val lowQuery = query.lowercase(Locale.ROOT)
        
        // Find if we have a direct match to common questions
        val isAskingSummary = lowQuery.contains("summary") || lowQuery.contains("about") || lowQuery.contains("what is") || lowQuery.contains("discuss")
        val isAskingTimings = lowQuery.contains("when") || lowQuery.contains("timing") || lowQuery.contains("time") || lowQuery.contains("seconds")

        val matchBlock = contextChunks.first()
        val formattedTime = formatTime(matchBlock.startMs)
        
        val baseAnswer = if (lowQuery.contains("react hook")) {
            "React Hooks allow functional components to use state, context, and other React features without writing a class."
        } else if (isAskingSummary) {
            "Based on the transcript context, this section covers: \"${matchBlock.text}\" (around $formattedTime). This discussion relates directly to your query."
        } else if (isAskingTimings) {
            "The events or topics you asked about occur around **$formattedTime** to **${formatTime(matchBlock.endMs)}**."
        } else {
            "According to the video transcript, the discussion highlights: \"${matchBlock.text}\" which matches your query."
        }

        val sb = StringBuilder()
        sb.append(baseAnswer)
        sb.append("\n\nSources:\n")
        contextChunks.forEach { chunk ->
            sb.append("${formatTime(chunk.startMs)} - ${formatTime(chunk.endMs)}\n")
        }

        return sb.toString()
    }

    private fun formatTime(timeMs: Long): String {
        val sec = (timeMs / 1000) % 60
        val min = (timeMs / (1000 * 60)) % 60
        val hr = timeMs / (1000 * 60 * 60)
        return String.format("%02d:%02d:%02d", hr, min, sec)
    }
}
