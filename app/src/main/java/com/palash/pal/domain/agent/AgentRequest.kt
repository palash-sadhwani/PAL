package com.palash.pal.domain.agent

import com.palash.pal.domain.model.Conversation
import com.palash.pal.domain.model.Message

data class AgentRequest(
    val conversation: Conversation,
    val messages: List<Message>,
    val userMessage: Message
)
