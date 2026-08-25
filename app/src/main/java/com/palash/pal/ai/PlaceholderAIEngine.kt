package com.palash.pal.ai

import com.palash.pal.domain.ai.AIRequest
import com.palash.pal.domain.ai.AIResponse
import com.palash.pal.domain.ai.LocalAIEngine

class PlaceholderAIEngine : LocalAIEngine {
    override suspend fun generateResponse(request: AIRequest): AIResponse {
        val preview = request.userMessage.content.trim().take(48)
        val content = if (preview.isBlank()) {
            "Placeholder response: PAL is ready, but no message was provided."
        } else {
            "Placeholder response: PAL received \"$preview\". Local on-device AI is not configured yet."
        }

        return AIResponse(content = content)
    }
}
