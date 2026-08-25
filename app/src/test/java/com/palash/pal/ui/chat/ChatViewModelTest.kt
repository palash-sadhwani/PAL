package com.palash.pal.ui.chat

import com.palash.pal.domain.agent.AgentRequest
import com.palash.pal.domain.agent.AgentResponse
import com.palash.pal.domain.agent.AgentRuntime
import com.palash.pal.domain.model.Conversation
import com.palash.pal.domain.model.Message
import com.palash.pal.domain.model.MessageRole
import com.palash.pal.domain.model.MessageStatus
import com.palash.pal.domain.repository.ChatRepository
import com.palash.pal.test.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun initializesWithExistingConversation() = runTest {
        val repository = FakeChatRepository()
        val conversation = repository.createConversation("PAL")
        repository.createUserMessage(conversation.id, "Hello PAL")
        val viewModel = ChatViewModel(repository, FixedAgentRuntime())

        advanceUntilIdle()

        assertEquals(conversation.id, viewModel.uiState.value.conversationId)
        assertEquals(1, viewModel.uiState.value.messages.size)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun sendsMessageAndPersistsAssistantResponse() = runTest {
        val repository = FakeChatRepository()
        val viewModel = ChatViewModel(repository, FixedAgentRuntime("Local reply"))

        advanceUntilIdle()
        viewModel.onDraftChanged("Hello PAL")
        viewModel.onSendClicked()
        advanceUntilIdle()

        val messages = repository.messages.value
        assertEquals(2, messages.size)
        assertEquals(MessageRole.USER, messages[0].role)
        assertEquals(MessageRole.ASSISTANT, messages[1].role)
        assertEquals(MessageStatus.COMPLETED, messages[1].status)
        assertTrue(viewModel.uiState.value.draftMessage.isEmpty())
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun reportsErrorWhenAgentFails() = runTest {
        val repository = FakeChatRepository()
        val viewModel = ChatViewModel(repository, ThrowingAgentRuntime())

        advanceUntilIdle()
        viewModel.onDraftChanged("Hello PAL")
        viewModel.onSendClicked()
        advanceUntilIdle()

        assertEquals(com.palash.pal.R.string.chat_error_generating_response, viewModel.uiState.value.errorMessageRes)
        assertEquals(MessageStatus.ERROR, repository.messages.value.last().status)
    }

    private class FakeChatRepository : ChatRepository {
        private val conversationState = MutableStateFlow<List<Conversation>>(emptyList())
        val messages = MutableStateFlow<List<Message>>(emptyList())
        private var activeConversation: Conversation? = null
        private var conversationCounter = 0
        private var messageCounter = 0

        override fun observeConversations(): Flow<List<Conversation>> = conversationState.asStateFlow()

        override suspend fun getActiveConversation(): Conversation? = activeConversation

        override suspend fun getOrCreateActiveConversation(): Conversation {
            return activeConversation ?: createConversation("PAL")
        }

        override fun observeMessages(conversationId: String): Flow<List<Message>> {
            return messages.map { items -> items.filter { it.conversationId == conversationId } }
        }

        override suspend fun getMessages(conversationId: String): List<Message> {
            return messages.value.filter { it.conversationId == conversationId }
        }

        override suspend fun createConversation(title: String): Conversation {
            conversationCounter += 1
            val conversation = Conversation(
                id = "conversation-$conversationCounter",
                title = title,
                createdAt = conversationCounter.toLong(),
                updatedAt = conversationCounter.toLong()
            )
            activeConversation = conversation
            conversationState.value = listOf(conversation)
            return conversation
        }

        override suspend fun createUserMessage(conversationId: String, content: String): Message {
            return createMessage(conversationId, MessageRole.USER, content, MessageStatus.SENT)
        }

        override suspend fun createAssistantMessage(
            conversationId: String,
            content: String,
            status: MessageStatus
        ): Message {
            return createMessage(conversationId, MessageRole.ASSISTANT, content, status)
        }

        override suspend fun updateMessage(message: Message): Message {
            messages.value = messages.value.map {
                if (it.id == message.id) message else it
            }
            return message
        }

        override suspend fun deleteConversation(conversationId: String) {
            activeConversation = null
            conversationState.value = emptyList()
            messages.value = emptyList()
        }

        private fun createMessage(
            conversationId: String,
            role: MessageRole,
            content: String,
            status: MessageStatus
        ): Message {
            messageCounter += 1
            val message = Message(
                id = "message-$messageCounter",
                conversationId = conversationId,
                role = role,
                content = content,
                createdAt = messageCounter.toLong(),
                status = status
            )
            messages.value = messages.value + message
            return message
        }
    }

    private class FixedAgentRuntime(
        private val response: String = "Engine reply"
    ) : AgentRuntime {
        override suspend fun generateResponse(request: AgentRequest): AgentResponse {
            return AgentResponse(assistantMessage = response)
        }
    }

    private class ThrowingAgentRuntime : AgentRuntime {
        override suspend fun generateResponse(request: AgentRequest): AgentResponse {
            throw IllegalStateException("Agent failed")
        }
    }
}
