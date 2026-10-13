package com.kigtts.plugin.api

import kotlinx.serialization.Serializable

@Serializable enum class ChatRole { USER, ASSISTANT, TOOL }
@Serializable enum class ChatMessageState { COMPLETE, STREAMING, FAILED, CANCELLED }
@Serializable data class ChatMessage(val id: String, val role: ChatRole, val text: String,
    val reasoning: String = "", val state: ChatMessageState = ChatMessageState.COMPLETE)
