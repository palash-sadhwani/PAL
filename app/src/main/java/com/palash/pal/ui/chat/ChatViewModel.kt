package com.palash.pal.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palash.pal.R
import com.palash.pal.domain.agent.AgentRequest
import com.palash.pal.domain.agent.AgentRuntime
import com.palash.pal.domain.model.Conversation
import com.palash.pal.domain.model.Message
import com.palash.pal.domain.model.MessageStatus
import com.palash.pal.domain.repository.ChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val agentRuntime: AgentRuntime
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var activeConversation: Conversation? = null
    private var messagesJob: Job? = null

    init {
        loadConversation()
    }

    fun onDraftChanged(value: String) {
        _uiState.update {
            it.copy(
                draftMessage = value,
                errorMessageRes = null
            )
        }
    }

    fun onSendClicked() {
        viewModelScope.launch {
            sendCurrentMessage()
        }
    }

    private fun loadConversation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessageRes = null) }
            runCatching { chatRepository.getOrCreateActiveConversation() }
                .onSuccess { conversation ->
                    activeConversation = conversation
                    _uiState.update {
                        it.copy(
                            conversationId = conversation.id,
                            conversationTitle = conversation.title,
                            isLoading = false,
                            errorMessageRes = null
                        )
                    }
                    observeMessages(conversation.id)
                }
                .onFailure {
                    logFailure("loadConversation", it)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessageRes = R.string.chat_error_opening_conversation
                        )
                    }
                }
        }
    }

    private fun observeMessages(conversationId: String) {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            chatRepository.observeMessages(conversationId).collectLatest { messages ->
                _uiState.update {
                    it.copy(messages = messages)
                }
            }
        }
    }

    private suspend fun sendCurrentMessage() {
        val trimmedMessage = _uiState.value.draftMessage.trim()
        if (trimmedMessage.isBlank()) {
            _uiState.update { it.copy(errorMessageRes = R.string.chat_error_empty_message) }
            return
        }

        val conversation = ensureConversation()
        _uiState.update {
            it.copy(
                draftMessage = "",
                isLoading = true,
                errorMessageRes = null
            )
        }

        var assistantMessage: Message? = null

        try {
            val userMessage = chatRepository.createUserMessage(conversation.id, trimmedMessage)
            val history = chatRepository.getMessages(conversation.id)
            assistantMessage = chatRepository.createAssistantMessage(
                conversationId = conversation.id,
                content = "",
                status = MessageStatus.GENERATING
            )

            val agentResponse = agentRuntime.generateResponse(
                AgentRequest(
                    conversation = conversation,
                    messages = history,
                    userMessage = userMessage
                )
            )

            chatRepository.updateMessage(
                assistantMessage.copy(
                    content = agentResponse.assistantMessage,
                    status = MessageStatus.COMPLETED
                )
            )
            _uiState.update { it.copy(isLoading = false) }
        } catch (exception: Exception) {
            logFailure("sendCurrentMessage", exception)
            val currentAssistantMessage = assistantMessage
            if (currentAssistantMessage != null) {
                runCatching {
                    chatRepository.updateMessage(
                        currentAssistantMessage.copy(
                            content = "PAL could not generate a response right now.",
                            status = MessageStatus.ERROR
                        )
                    )
                }
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessageRes = R.string.chat_error_generating_response
                )
            }
        }
    }

    private suspend fun ensureConversation(): Conversation {
        val currentConversation = activeConversation
        if (currentConversation != null) {
            return currentConversation
        }

        val conversation = chatRepository.getOrCreateActiveConversation()
        activeConversation = conversation
        _uiState.update {
            it.copy(
                conversationId = conversation.id,
                conversationTitle = conversation.title
            )
        }
        observeMessages(conversation.id)
        return conversation
    }

    private fun logFailure(operation: String, throwable: Throwable) {
        System.err.println("$operation failed: ${throwable.javaClass.simpleName}")
    }
}
