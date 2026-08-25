package com.palash.pal.ai

import com.palash.pal.domain.ai.AIRequest
import com.palash.pal.domain.model.Conversation
import com.palash.pal.domain.model.Message
import com.palash.pal.domain.model.MessageRole
import com.palash.pal.domain.model.MessageStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceholderAIEngineTest {
    @Test
    fun returnsDeterministicLocalResponse() = runTest {
        val engine = PlaceholderAIEngine()
        val request = sampleRequest("Hello PAL")

        val first = engine.generateResponse(request)
        val second = engine.generateResponse(request)

        assertEquals(first, second)
        assertTrue(first.content.contains("Placeholder response"))
    }

    private fun sampleRequest(userText: String): AIRequest {
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
            content = userText,
            createdAt = 2L,
            status = MessageStatus.SENT
        )
        return AIRequest(
            conversation = conversation,
            messages = listOf(userMessage),
            userMessage = userMessage
        )
    }
}
