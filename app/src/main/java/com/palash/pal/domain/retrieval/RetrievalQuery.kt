package com.palash.pal.domain.retrieval

data class RetrievalQuery(
    val text: String,
    val maxResults: Int = 5
)
