package com.palash.pal.domain.retrieval

interface RetrievalEngine {
    suspend fun retrieve(query: RetrievalQuery): List<RetrievalResult>
}
