package com.kigtts.plugin.api

import kotlinx.serialization.Serializable

@Serializable enum class ChatRole { USER, ASSISTANT, TOOL }
@Serializable enum class ChatMessageState { COMPLETE, STREAMING, FAILED, CANCELLED }
@Serializable data class ChatMessage(val id: String, val role: ChatRole, val text: String,
    val reasoning: String = "", val state: ChatMessageState = ChatMessageState.COMPLETE,
    val summary: String = "", val toolName: String = "", val detail: String = "", val icon: PluginIcon = PluginIcon.INFO) {
    constructor(id: String, role: ChatRole, text: String, reasoning: String = "", state: ChatMessageState = ChatMessageState.COMPLETE)
        : this(id, role, text, reasoning, state, "", "", "", PluginIcon.INFO)
    fun copy(id: String = this.id, role: ChatRole = this.role, text: String = this.text,
        reasoning: String = this.reasoning, state: ChatMessageState = this.state): ChatMessage =
        ChatMessage(id, role, text, reasoning, state, summary, toolName, detail, icon)
}
@Serializable enum class ChatControlKind { MODEL, ACCESS }
@Serializable data class ChatOption(val action: String, val label: String, val value: String = "")
@Serializable data class ChatControl(val title: String, val options: List<ChatOption>, val selected: String,
    val kind: ChatControlKind = ChatControlKind.MODEL)
@Serializable data class ChatApproval(val title: String, val approveAction: String, val rejectAction: String,
    val detail: String = "", val reviewAction: String? = null)
