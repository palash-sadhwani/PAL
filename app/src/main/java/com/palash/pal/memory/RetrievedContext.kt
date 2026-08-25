package com.palash.pal.memory

import com.palash.pal.domain.retrieval.RetrievalResult

data class RetrievedContext(
    val results: List<RetrievalResult> = emptyList()
)
