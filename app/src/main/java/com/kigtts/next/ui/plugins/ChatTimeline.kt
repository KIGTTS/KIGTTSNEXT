package com.kigtts.next.ui.plugins

import com.kigtts.plugin.api.*

internal data class ChatExecutionStep(val message: ChatMessage, val thought: Boolean) {
    val key get() = (if (thought) "thought/" else "tool/") + message.id
    val running get() = if (thought) message.thinking else message.state == ChatMessageState.STREAMING
}
internal data class ChatTimelineRow(val key: String, val steps: List<ChatExecutionStep> = emptyList(), val answer: ChatMessage? = null)

/** Only adjacent finished execution steps merge. A live step and each answer keep their own identity. */
internal fun chatTimeline(messages: List<ChatMessage>): List<ChatTimelineRow> = buildList {
    val completed = mutableListOf<ChatExecutionStep>()
    fun flush() { if (completed.isNotEmpty()) { add(ChatTimelineRow(completed.first().key, completed.toList())); completed.clear() } }
    fun step(value: ChatExecutionStep) {
        if (value.running) { flush(); add(ChatTimelineRow(value.key, listOf(value))) }
        else completed += value
    }
    messages.forEach { message ->
        if (message.role == ChatRole.TOOL) step(ChatExecutionStep(message, false))
        else {
            if (message.role == ChatRole.ASSISTANT && (message.thinking || message.hasReasoning || message.reasoning.isNotBlank() || message.summary.isNotBlank()))
                step(ChatExecutionStep(message, true))
            val show = message.role != ChatRole.ASSISTANT || message.text.isNotBlank() || message.images.isNotEmpty() ||
                message.options.isNotEmpty() || message.files.isNotEmpty() ||
                message.state == ChatMessageState.FAILED || message.state == ChatMessageState.CANCELLED ||
                message.state == ChatMessageState.STREAMING && !message.thinking && !message.hasReasoning && message.reasoning.isBlank()
            if (show) { flush(); add(ChatTimelineRow("answer/" + message.id, answer = message)) }
        }
    }
    flush()
}
