package com.palash.pal.agent

import com.palash.pal.domain.ai.AIRequest
import com.palash.pal.domain.ai.AIResponse
import com.palash.pal.domain.ai.LocalAIEngine
import com.palash.pal.domain.agent.AgentRequest
import com.palash.pal.domain.model.Conversation
import com.palash.pal.domain.model.Message
import com.palash.pal.domain.model.MessageRole
import com.palash.pal.domain.model.MessageStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultAgentRuntimeTest {
    @Test
    fun passesRequestThroughToLocalAiEngine() = runTest {
        val engine = RecordingEngine()
        val runtime = DefaultAgentRuntime(engine)
        val request = sampleRequest()

        val response = runtime.generateResponse(request)

        assertEquals("Engine reply", response.assistantMessage)
        assertEquals(request.conversation, engine.lastRequest?.conversation)
        assertEquals(request.userMessage, engine.lastRequest?.userMessage)
    }

    private fun sampleRequest(): AgentRequest {
        val conversation = Conversation(
            id = "conversation-1",
            title = "PAL",
            createdAt = 1L,
            updatedAt = 1L
        )
        val userMessage = Message(
            id = "message-1",
            conversationId = conversation.id,
            role = MessageRole.USER,
            content = "Hello PAL",
            createdAt = 2L,
            status = MessageStatus.SENT
        )
        return AgentRequest(
            conversation = conversation,
            messages = listOf(userMessage),
            userMessage = userMessage
        )
    }

    private class RecordingEngine : LocalAIEngine {
        var lastRequest: AIRequest? = null

        override suspend fun generateResponse(request: AIRequest): AIResponse {
            lastRequest = request
            return AIResponse(content = "Engine reply")
        }
    }
}
