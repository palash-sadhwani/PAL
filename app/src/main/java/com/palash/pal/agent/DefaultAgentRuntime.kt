package com.palash.pal.agent

import com.palash.pal.domain.ai.AIRequest
import com.palash.pal.domain.ai.LocalAIEngine
import com.palash.pal.domain.agent.AgentRequest
import com.palash.pal.domain.agent.AgentResponse
import com.palash.pal.domain.agent.AgentRuntime

class DefaultAgentRuntime(
    private val localAIEngine: LocalAIEngine
) : AgentRuntime {
    override suspend fun generateResponse(request: AgentRequest): AgentResponse {
        val aiResponse = localAIEngine.generateResponse(
            AIRequest(
                conversation = request.conversation,
                messages = request.messages,
                userMessage = request.userMessage
            )
        )

        return AgentResponse(assistantMessage = aiResponse.content)
    }
}
