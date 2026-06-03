package com.example.data.localai

import kotlin.math.sqrt

object CosineSimilarity {
    /**
     * Calculates the cosine similarity score between two vector representations.
     */
    fun calculate(v1: Map<String, Float>, v2: Map<String, Float>): Float {
        if (v1.isEmpty() || v2.isEmpty()) return 0.0f

        var dotProduct = 0.0f
        var normV1 = 0.0f
        var normV2 = 0.0f

        // 1. Calculate Dot Product and Norm of V1
        v1.forEach { (word, weight) ->
            dotProduct += weight * (v2[word] ?: 0.0f)
            normV1 += weight * weight
        }

        // 2. Calculate Norm of V2
        v2.forEach { (_, weight) ->
            normV2 += weight * weight
        }

        if (normV1 == 0.0f || normV2 == 0.0f) return 0.0f

        return (dotProduct / (sqrt(normV1.toDouble()) * sqrt(normV2.toDouble()))).toFloat()
    }
}
