package com.palash.pal.domain

import com.palash.pal.domain.model.Conversation
import com.palash.pal.domain.model.Message
import com.palash.pal.domain.model.MessageRole
import com.palash.pal.domain.model.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DomainModelsTest {
    @Test
    fun conversationCopiesCleanly() {
        val original = Conversation(
            id = "conversation-1",
            title = "PAL",
            createdAt = 10L,
            updatedAt = 10L
        )

        val updated = original.copy(updatedAt = 20L)

        assertEquals("conversation-1", updated.id)
        assertEquals(20L, updated.updatedAt)
    }

    @Test
    fun messageCarriesRoleAndStatus() {
        val message = Message(
            id = "message-1",
            conversationId = "conversation-1",
            role = MessageRole.USER,
            content = "Hello PAL",
            createdAt = 10L,
            status = MessageStatus.SENT
        )

        assertEquals(MessageRole.USER, message.role)
        assertEquals(MessageStatus.SENT, message.status)
    }
}
