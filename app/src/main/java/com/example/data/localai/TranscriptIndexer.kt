package com.example.data.localai

import com.example.data.localai.model.RagChunk
import com.example.data.model.SubtitleBlock
import kotlin.math.log10

object TranscriptIndexer {
    private val stopWords = setOf(
        "the", "a", "an", "and", "or", "but", "is", "are", "was", "were", "to", "of", "in", "for", "on", "with", "at", "by", "from"
    )

    /**
     * Chunks a list of subtitle blocks into overlapping segment windows (e.g. 3 blocks per chunk, shifting by 2)
     * and calculates TF-IDF vector weights for each term.
     */
    fun index(blocks: List<SubtitleBlock>): List<RagChunk> {
        if (blocks.isEmpty()) return emptyList()

        val chunks = mutableListOf<RagChunk>()
        val chunkSize = 3
        val stride = 2

        // 1. Group subtitle blocks into chunks
        var i = 0
        var chunkIdCounter = 1
        while (i < blocks.size) {
            val end = minOf(i + chunkSize, blocks.size)
            val subList = blocks.subList(i, end)
            
            val textContent = subList.joinToString(" ") { it.text }
            val startMs = subList.first().startTimeMs
            val endMs = subList.last().endTimeMs

            chunks.add(
                RagChunk(
                    id = "chunk_${chunkIdCounter++}",
                    text = textContent,
                    startMs = startMs,
                    endMs = endMs
                )
            )

            if (end == blocks.size) break
            i += stride
        }

        // 2. Count term frequencies (TF) and document frequencies (DF)
        val chunkWords = chunks.map { chunk ->
            chunk.text.lowercase()
                .split(Regex("[^a-zA-Z0-9']"))
                .filter { it.isNotEmpty() && !stopWords.contains(it) }
        }

        val allDocsCount = chunks.size.toFloat()
        val docFrequency = mutableMapOf<String, Int>()

        // Count Document Frequencies (DF)
        chunkWords.forEach { words ->
            val uniqueWords = words.toSet()
            uniqueWords.forEach { word ->
                docFrequency[word] = docFrequency.getOrDefault(word, 0) + 1
            }
        }

        // 3. Compute TF-IDF weights for each chunk
        val indexedChunks = chunks.mapIndexed { index, chunk ->
            val words = chunkWords[index]
            val totalWordsInDoc = words.size.toFloat()
            if (totalWordsInDoc == 0f) {
                return@mapIndexed chunk
            }

            val termCounts = words.groupingBy { it }.eachCount()
            val tfIdfMap = mutableMapOf<String, Float>()

            termCounts.forEach { (word, count) ->
                val tf = count / totalWordsInDoc
                val df = docFrequency[word] ?: 1
                val idf = log10(allDocsCount / df.toFloat())
                tfIdfMap[word] = (tf * idf).toFloat()
            }

            chunk.copy(tokenWeights = tfIdfMap)
        }

        return indexedChunks
    }
}
