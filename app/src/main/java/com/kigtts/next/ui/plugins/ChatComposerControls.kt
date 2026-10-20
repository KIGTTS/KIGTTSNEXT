package com.kigtts.next.ui.plugins

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.plugin.api.*

@Composable internal fun ChatComposerControls(chat: PageBlock.Chat, enabled: Boolean,
    invoke: (String, Map<String, String>) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        AnimatedVisibility(chat.approval != null) {
            chat.approval?.let { approval ->
                Surface(Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("plugin-chat-approval"),
                    shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(approval.title, style = MaterialTheme.typography.titleSmall)
                        if (approval.detail.isNotBlank()) Text(approval.detail, style = MaterialTheme.typography.bodySmall, maxLines = 5)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = feedbackClick { invoke(approval.approveAction, mapOf("_agentApprove" to "true")) }, enabled = enabled) { Text("批准并继续") }
                            TextButton(onClick = feedbackClick { invoke(approval.rejectAction, emptyMap()) }, enabled = enabled) { Text("拒绝") }
                            approval.reviewAction?.let { action -> TextButton(onClick = feedbackClick { invoke(action, emptyMap()) }, enabled = enabled) { Text("查看详情") } }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chat.controls.forEach { control ->
                var expanded by remember(control.kind) { mutableStateOf(false) }
                Box {
                    AssistChip(onClick = feedbackClick { expanded = true }, enabled = enabled,
                        label = { Text(control.options.firstOrNull { it.action == control.selected }?.label ?: control.title) },
                        leadingIcon = { Icon(if (control.kind == ChatControlKind.ACCESS) Icons.Default.Security else Icons.Default.AutoAwesome, null, Modifier.size(18.dp)) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("plugin-chat-control/" + control.kind.name))
                    DropdownMenu(expanded, { expanded = false }) {
                        control.options.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = feedbackClick {
                            expanded = false
                            invoke(option.action, if (control.kind == ChatControlKind.ACCESS) mapOf("_agentAccess" to option.value) else emptyMap())
                        }) }
                    }
                }
            }
        }
    }
}
