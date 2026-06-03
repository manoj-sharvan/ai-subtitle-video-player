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
        val isAskingSummary = lowQuery.contains("summary") || lowQuery.contains("about") || lowQuery.contains("what is")
        val isAskingTimings = lowQuery.contains("when") || lowQuery.contains("timing") || lowQuery.contains("time") || lowQuery.contains("seconds")

        val matchBlock = contextChunks.first()
        val formattedTime = formatTime(matchBlock.startMs)
        
        val sb = StringBuilder()
        if (isAskingSummary) {
            sb.append("Based on the transcript context, this section covers: \"${matchBlock.text}\" (around $formattedTime).\n\n")
            sb.append("This discussion relates directly to your query about '${query}'.")
        } else if (isAskingTimings) {
            sb.append("The events or topics you asked about occur around **$formattedTime** to **${formatTime(matchBlock.endMs)}**.\n\n")
            sb.append("Transcript reference: \"${matchBlock.text}\"")
        } else {
            sb.append("According to the video transcript at **$formattedTime**:\n")
            sb.append("\"${matchBlock.text}\"\n\n")
            sb.append("This is the most relevant section found in relation to: \"${query}\".")
        }

        // Add additional references if available
        if (contextChunks.size > 1) {
            sb.append("\n\n*See also section at ${formatTime(contextChunks[1].startMs)}:* \"${contextChunks[1].text.take(80)}...\"")
        }

        return sb.toString()
    }

    private fun formatTime(timeMs: Long): String {
        val sec = (timeMs / 1000) % 60
        val min = (timeMs / (1000 * 60)) % 60
        val hr = timeMs / (1000 * 60 * 60)
        return if (hr > 0) {
            String.format("%02d:%02d:%02d", hr, min, sec)
        } else {
            String.format("%02d:%02d", min, sec)
        }
    }
}
