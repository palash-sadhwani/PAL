package com.palash.pal.memory

interface PersonalMemoryStore {
    suspend fun search(query: String): RetrievedContext
}
