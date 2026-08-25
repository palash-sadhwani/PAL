package com.palash.pal.ai

import com.palash.pal.domain.ai.AIRequest
import com.palash.pal.domain.model.Conversation
import com.palash.pal.domain.model.Message
import com.palash.pal.domain.model.MessageRole
import com.palash.pal.domain.model.MessageStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class OnDeviceAIEngineTest {
    @Test
    fun reportsModelReadinessInsteadOfSilentlyUsingPlaceholder() = runTest {
        val engine = OnDeviceAIEngine(
            nativeEngine = OnDeviceLLMNative(),
            fallbackEngine = PlaceholderAIEngine(),
            modelPath = "/nonexistent/model.gguf"
        )

        val response = engine.generateResponse(sampleRequest("Hello from PAL"))

        assertTrue(response.content.contains("MODEL_NOT_READY") || response.content.contains("NATIVE_LIBRARY_UNAVAILABLE"))
        assertTrue(!response.content.contains("Placeholder response"))
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
