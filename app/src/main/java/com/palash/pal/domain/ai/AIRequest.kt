package com.palash.pal.domain.ai

import com.palash.pal.domain.model.Conversation
import com.palash.pal.domain.model.Message

data class AIRequest(
    val conversation: Conversation,
    val messages: List<Message>,
    val userMessage: Message
)
