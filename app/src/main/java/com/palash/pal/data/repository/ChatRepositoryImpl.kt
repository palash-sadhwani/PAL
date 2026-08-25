package com.palash.pal.data.repository

import androidx.room.withTransaction
import com.palash.pal.core.time.TimeProvider
import com.palash.pal.data.local.dao.ConversationDao
import com.palash.pal.data.local.dao.MessageDao
import com.palash.pal.data.local.db.PalDatabase
import com.palash.pal.data.local.entity.ConversationEntity
import com.palash.pal.data.local.entity.MessageEntity
import com.palash.pal.domain.model.Conversation
import com.palash.pal.domain.model.Message
import com.palash.pal.domain.model.MessageRole
import com.palash.pal.domain.model.MessageStatus
import com.palash.pal.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class ChatRepositoryImpl(
    private val database: PalDatabase,
    private val timeProvider: TimeProvider
) : ChatRepository {
    private val conversationDao: ConversationDao = database.conversationDao()
    private val messageDao: MessageDao = database.messageDao()

    override fun observeConversations(): Flow<List<Conversation>> {
        return conversationDao.observeConversations().map { conversations ->
            conversations.map { it.toDomain() }
        }
    }

    override suspend fun getActiveConversation(): Conversation? {
        return conversationDao.getLatestConversation()?.toDomain()
    }

    override suspend fun getOrCreateActiveConversation(): Conversation {
        return database.withTransaction {
            conversationDao.getLatestConversation()?.toDomain()
                ?: createConversationLocked(DEFAULT_CONVERSATION_TITLE)
        }
    }

    override fun observeMessages(conversationId: String): Flow<List<Message>> {
        return messageDao.observeMessages(conversationId).map { messages ->
            messages.map { it.toDomain() }
        }
    }

    override suspend fun getMessages(conversationId: String): List<Message> {
        return messageDao.getMessages(conversationId).map { it.toDomain() }
    }

    override suspend fun createConversation(title: String): Conversation {
        return database.withTransaction {
            createConversationLocked(title)
        }
    }

    override suspend fun createUserMessage(conversationId: String, content: String): Message {
        return createMessage(
            conversationId = conversationId,
            role = MessageRole.USER,
            content = content,
            status = MessageStatus.SENT
        )
    }

    override suspend fun createAssistantMessage(
        conversationId: String,
        content: String,
        status: MessageStatus
    ): Message {
        return createMessage(
            conversationId = conversationId,
            role = MessageRole.ASSISTANT,
            content = content,
            status = status
        )
    }

    override suspend fun updateMessage(message: Message): Message {
        database.withTransaction {
            requireConversationExists(message.conversationId)
            messageDao.updateMessage(message.toEntity())
            conversationDao.updateConversationTimestamp(message.conversationId, timeProvider.nowMillis())
        }
        return message
    }

    override suspend fun deleteConversation(conversationId: String) {
        database.withTransaction {
            val conversation = requireConversationExists(conversationId)
            conversationDao.deleteConversation(conversation)
        }
    }

    private suspend fun createMessage(
        conversationId: String,
        role: MessageRole,
        content: String,
        status: MessageStatus
    ): Message {
        val now = timeProvider.nowMillis()
        val message = Message(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            role = role,
            content = content,
            createdAt = now,
            status = status
        )

        database.withTransaction {
            requireConversationExists(conversationId)
            messageDao.insertMessage(message.toEntity())
            conversationDao.updateConversationTimestamp(conversationId, now)
        }

        return message
    }

    private suspend fun createConversationLocked(title: String): Conversation {
        val now = timeProvider.nowMillis()
        val conversation = Conversation(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = now,
            updatedAt = now
        )

        conversationDao.insertConversation(conversation.toEntity())
        return conversation
    }

    private suspend fun requireConversationExists(conversationId: String): ConversationEntity {
        return conversationDao.getConversation(conversationId)
            ?: throw IllegalStateException("Conversation not found: $conversationId")
    }

    private fun ConversationEntity.toDomain(): Conversation {
        return Conversation(
            id = id,
            title = title,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun Conversation.toEntity(): ConversationEntity {
        return ConversationEntity(
            id = id,
            title = title,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun MessageEntity.toDomain(): Message {
        return Message(
            id = id,
            conversationId = conversationId,
            role = role,
            content = content,
            createdAt = createdAt,
            status = status
        )
    }

    private fun Message.toEntity(): MessageEntity {
        return MessageEntity(
            id = id,
            conversationId = conversationId,
            role = role,
            content = content,
            createdAt = createdAt,
            status = status
        )
    }

    private companion object {
        const val DEFAULT_CONVERSATION_TITLE = "PAL"
    }
}
