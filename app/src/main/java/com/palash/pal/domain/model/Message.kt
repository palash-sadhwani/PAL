package com.palash.pal.domain.model

data class Message(
    val id: String,
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val createdAt: Long,
    val status: MessageStatus
)
