package com.palash.pal.domain.retrieval

data class RetrievalResult(
    val id: String,
    val title: String? = null,
    val content: String,
    val score: Double? = null
)
