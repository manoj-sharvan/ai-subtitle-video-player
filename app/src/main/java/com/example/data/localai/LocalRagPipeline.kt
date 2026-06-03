package com.example.data.localai

import com.example.data.localai.model.RagChunk
import com.example.data.model.SubtitleBlock

class LocalRagPipeline {
    private var indexedChunks = listOf<RagChunk>()

    /**
     * Re-indexes the transcript chunks for a video.
     */
    fun indexSubtitles(blocks: List<SubtitleBlock>) {
        indexedChunks = TfIdfIndexer.index(blocks)
    }

    /**
     * Searches the local indexed transcript database and returns the top-3 matching chunks.
     */
    fun search(query: String): List<RagChunk> {
        if (query.trim().isEmpty() || indexedChunks.isEmpty()) {
            return emptyList()
        }

        // 1. Build TF vector for query
        val queryWords = query.lowercase()
            .split(Regex("[^a-zA-Z0-9']"))
            .filter { it.isNotEmpty() }
        
        if (queryWords.isEmpty()) return emptyList()

        val queryTf = queryWords.groupingBy { it }.eachCount()
        val totalQueryWords = queryWords.size.toFloat()
        val queryWeights = queryTf.mapValues { (_, count) -> count / totalQueryWords }

        // 2. Score similarity for each chunk
        val scoredChunks = indexedChunks.map { chunk ->
            val score = CosineSimilarity.calculate(queryWeights, chunk.tokenWeights)
            chunk to score
        }

        // 3. Filter and return top-3 matching chunks
        return scoredChunks
            .filter { it.second > 0.0f }
            .sortedByDescending { it.second }
            .take(3)
            .map { it.first }
    }
}
