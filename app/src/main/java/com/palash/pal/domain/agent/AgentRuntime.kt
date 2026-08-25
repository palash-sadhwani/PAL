package com.palash.pal.domain.agent

interface AgentRuntime {
    suspend fun generateResponse(request: AgentRequest): AgentResponse
}
