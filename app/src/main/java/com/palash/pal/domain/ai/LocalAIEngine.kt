package com.palash.pal.domain.ai

interface LocalAIEngine {
    suspend fun generateResponse(request: AIRequest): AIResponse
}
