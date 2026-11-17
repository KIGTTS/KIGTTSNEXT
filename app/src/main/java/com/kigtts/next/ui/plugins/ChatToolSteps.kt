package com.kigtts.next.ui.plugins

import com.kigtts.next.i18n.uiText

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.vector
import com.kigtts.plugin.api.*

@Composable internal fun ChatToolGroup(messages: List<ChatMessage>, readingInteraction: () -> Unit = {}) {
    ChatExecutionGroup(messages.map { ChatExecutionStep(it, false) }, readingInteraction, forceGroup = true)
}

@Composable internal fun ChatExecutionGroup(steps: List<ChatExecutionStep>, readingInteraction: () -> Unit = {}, forceGroup: Boolean = false) {
    if (steps.size == 1 && !forceGroup) {
        val step = steps.single()
        if (step.thought) ChatThought(step.message, readingInteraction)
        else Surface(Modifier.fillMaxWidth(.94f).testTag("chat-tools/" + step.message.id), shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer) { ChatToolItem(step.message, readingInteraction) }
        return
    }
    var expanded by remember(steps.first().key) { mutableStateOf(false) }
    val firstId = steps.first().message.id
    val allTools = steps.none { it.thought }
    Surface(Modifier.fillMaxWidth(.94f).testTag("chat-tools/" + firstId), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            ChatStepHeader(if (allTools) uiText("chat.tools.count", "count" to steps.size.toString()) else uiText("chat.steps.count", "count" to steps.size.toString()),
                if (allTools) Icons.Default.Build else Icons.Default.AutoAwesome, expanded, steps.any { it.running },
                Modifier.testTag("chat-tools-toggle/" + firstId)) { readingInteraction(); expanded = !expanded }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                    steps.forEach { step -> key(step.key) {
                        if (step.thought) ChatThought(step.message, readingInteraction, nested = true)
                        else ChatToolItem(step.message, readingInteraction)
                    } }
                }
            }
        }
    }
}

@Composable private fun ChatToolItem(message: ChatMessage, readingInteraction: () -> Unit) {
    var expanded by remember(message.id) { mutableStateOf(false) }
    Column {
        ChatStepHeader(message.toolName.ifBlank { uiText("chat.tools.name") }, message.icon.vector(), expanded,
            message.state == ChatMessageState.STREAMING, Modifier.testTag("chat-tool/" + message.id),
            status = if (message.state == ChatMessageState.STREAMING) uiText("chat.tools.running") else message.summary.ifBlank { uiText("action.done") }) {
            readingInteraction(); expanded = !expanded
        }
        AnimatedVisibility(expanded) { Column(Modifier.padding(start = 12.dp)) {
            if (message.detail.isNotBlank()) ToolSection(uiText("chat.tools.process"), message.detail, readingInteraction)
            if (message.text.isNotBlank()) ToolSection(uiText("chat.tools.result"), message.text, readingInteraction)
        } }
    }
}

@Composable private fun ToolSection(title: String, text: String, readingInteraction: () -> Unit) {
    var expanded by remember(title) { mutableStateOf(false) }
    Column {
        TextButton(onClick = feedbackClick { readingInteraction(); expanded = !expanded }) {
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(16.dp))
            Text(title, style = MaterialTheme.typography.labelMedium)
        }
        AnimatedVisibility(expanded) { ChatMarkdownCode(text, "json", true) }
    }
}
