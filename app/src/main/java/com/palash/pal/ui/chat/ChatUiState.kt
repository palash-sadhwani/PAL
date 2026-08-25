package com.palash.pal.ui.chat

import androidx.annotation.StringRes
import com.palash.pal.domain.model.Message

data class ChatUiState(
    val conversationId: String? = null,
    val conversationTitle: String = "",
    val draftMessage: String = "",
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = true,
    @StringRes val errorMessageRes: Int? = null
)
