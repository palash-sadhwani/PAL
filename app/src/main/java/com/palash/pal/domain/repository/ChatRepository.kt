package com.palash.pal.domain.repository

import com.palash.pal.domain.model.Conversation
import com.palash.pal.domain.model.Message
import com.palash.pal.domain.model.MessageStatus
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeConversations(): Flow<List<Conversation>>

    suspend fun getActiveConversation(): Conversation?

    suspend fun getOrCreateActiveConversation(): Conversation

    fun observeMessages(conversationId: String): Flow<List<Message>>

    suspend fun getMessages(conversationId: String): List<Message>

    suspend fun createConversation(title: String): Conversation

    suspend fun createUserMessage(conversationId: String, content: String): Message

    suspend fun createAssistantMessage(
        conversationId: String,
        content: String,
        status: MessageStatus
    ): Message

    suspend fun updateMessage(message: Message): Message

    suspend fun deleteConversation(conversationId: String)
}
