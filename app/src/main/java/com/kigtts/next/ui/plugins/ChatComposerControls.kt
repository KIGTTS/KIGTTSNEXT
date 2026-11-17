package com.kigtts.next.ui.plugins

import com.kigtts.next.i18n.uiText
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
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.next.plugins.control.LocalAgentAccessPolicy
import com.kigtts.plugin.api.*

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ChatComposerControls(chat: PageBlock.Chat, enabled: Boolean, owner: String = "", modifier: Modifier = Modifier,
    invoke: (String, Map<String, String>) -> Unit) {
    val authority = LocalAgentAccessPolicy.current
    var warning by remember { mutableStateOf<ChatOption?>(null) }
    warning?.let { option ->
        MotionAlertDialog(onDismissRequest = { warning = null }, title = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(uiText("chat.access.warning"), Modifier.weight(1f))
                Icon(Icons.Default.GppMaybe, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.error)
            }
        }, text = { Text(uiText("chat.access.details")) },
            confirmButton = { TextButton(onClick = feedbackClick {
                warning = null; invoke(option.action, mapOf("_agentAccess" to "full", "_agentFullConfirmed" to "true"))
            }) { Text(uiText("chat.access.confirm")) } },
            dismissButton = { TextButton(onClick = feedbackClick { warning = null }) { Text(uiText("action.cancel")) } })
    }
        val controls = remember(chat.controls) { chat.controls.filter { it.kind != ChatControlKind.REASONING &&
            (it.kind != ChatControlKind.PROVIDER || chat.controls.none { model -> model.kind == ChatControlKind.MODEL }) } }
        val scrolling = controls.size > 2
        Row(if (scrolling) modifier.horizontalScroll(rememberScrollState()) else modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            controls.forEach { control ->
                val slot = if (controls.size == 2) Modifier.weight(1f) else Modifier.widthIn(max = 154.dp)
                if (control.kind == ChatControlKind.MODEL) {
                    ChatModelControl(control, enabled, invoke, chat.controls.firstOrNull { it.kind == ChatControlKind.REASONING }, slot)
                    return@forEach
                }
                var expanded by remember(control.kind) { mutableStateOf(false) }
                Box(slot) {
                    AssistChip(onClick = feedbackClick { expanded = true }, enabled = enabled,
                        label = { Text(control.options.firstOrNull { it.action == control.selected }?.label ?: control.title, Modifier.widthIn(max = 112.dp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(when (control.kind) { ChatControlKind.ACCESS -> accessIcon(control.options.firstOrNull { it.action == control.selected }?.value); ChatControlKind.PROVIDER -> Icons.Default.Cloud; else -> Icons.Default.AutoAwesome }, null, Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("plugin-chat-control/" + control.kind.name))
                    DropdownMenu(expanded, { expanded = false }) {
                        control.options.forEach { option -> DropdownMenuItem(text = { Text(option.label) },
                            leadingIcon = if (control.kind == ChatControlKind.ACCESS) ({ Icon(accessIcon(option.value), null) }) else null,
                            onClick = feedbackClick {
                            expanded = false
                            if (control.kind == ChatControlKind.ACCESS && option.value == "full" && authority?.warningShown(owner) != true) warning = option
                            else invoke(option.action, if (control.kind == ChatControlKind.ACCESS)
                                mapOf("_agentAccess" to option.value) + if (option.value == "full") mapOf("_agentFullConfirmed" to "true") else emptyMap()
                                else emptyMap())
                        }) }
                    }
                }
            }
        }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ChatApprovalPanel(chat: PageBlock.Chat, enabled: Boolean, invoke: (String, Map<String, String>) -> Unit) {
        AnimatedVisibility(chat.approval != null) {
            chat.approval?.let { approval ->
                Surface(Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("plugin-chat-approval"),
                    shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(approval.title, style = MaterialTheme.typography.titleSmall)
                        if (approval.detail.isNotBlank()) Text(approval.detail, style = MaterialTheme.typography.bodySmall, maxLines = 5)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Button(onClick = feedbackClick { invoke(approval.approveAction, mapOf("_agentApprove" to "true")) }, enabled = enabled,
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 1.dp)) {
                                Icon(Icons.Default.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(uiText("chat.approve"))
                            }
                            TextButton(onClick = feedbackClick { invoke(approval.rejectAction, emptyMap()) }, enabled = enabled) {
                                Icon(Icons.Default.Close, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(uiText("chat.reject"))
                            }
                            approval.reviewAction?.let { action -> TextButton(onClick = feedbackClick { invoke(action, emptyMap()) }, enabled = enabled) { Text(uiText("chat.details")) } }
                        }
                    }
                }
            }
        }
}

private fun accessIcon(value: String?) = when (value) {
    "full" -> Icons.Default.GppMaybe
    "assist" -> Icons.Default.VerifiedUser
    else -> Icons.Default.BackHand
}
