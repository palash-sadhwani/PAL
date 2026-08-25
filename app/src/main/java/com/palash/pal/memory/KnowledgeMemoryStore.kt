package com.palash.pal.memory

interface KnowledgeMemoryStore {
    suspend fun search(query: String): RetrievedContext
}
