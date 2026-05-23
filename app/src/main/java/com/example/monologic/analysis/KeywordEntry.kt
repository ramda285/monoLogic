package com.example.monologic.analysis

import kotlinx.serialization.Serializable

@Serializable
data class KeywordEntry(
    val word: String,
    val sentiment: String   // Sentiment.POSITIVE / NEGATIVE / NEUTRAL
)

object Sentiment {
    const val POSITIVE = "POSITIVE"
    const val NEGATIVE = "NEGATIVE"
    const val NEUTRAL  = "NEUTRAL"
}
