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
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.next.plugins.control.LocalAgentAccessPolicy
import com.kigtts.plugin.api.*

@Composable internal fun ChatComposerControls(chat: PageBlock.Chat, enabled: Boolean, owner: String = "",
    invoke: (String, Map<String, String>) -> Unit) {
    val authority = LocalAgentAccessPolicy.current
    var warning by remember { mutableStateOf<ChatOption?>(null) }
    warning?.let { option ->
        MotionAlertDialog(onDismissRequest = { warning = null }, title = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("开启完全访问？", Modifier.weight(1f))
                Icon(Icons.Default.GppMaybe, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.error)
            }
        }, text = { Text("助手将可以修改或删除内容、安装和调试插件，并执行软件已开放的操作，无需逐项批准。请只在信任本次任务时开启。系统相机、麦克风等设备权限仍由系统管理。") },
            confirmButton = { TextButton(onClick = feedbackClick {
                warning = null; invoke(option.action, mapOf("_agentAccess" to "full", "_agentFullConfirmed" to "true"))
            }) { Text("开启完全访问") } },
            dismissButton = { TextButton(onClick = feedbackClick { warning = null }) { Text("取消") } })
    }
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
            chat.controls.filter { it.kind != ChatControlKind.REASONING &&
                (it.kind != ChatControlKind.PROVIDER || chat.controls.none { model -> model.kind == ChatControlKind.MODEL }) }.forEach { control ->
                if (control.kind == ChatControlKind.MODEL) {
                    ChatModelControl(control, enabled, invoke, chat.controls.firstOrNull { it.kind == ChatControlKind.REASONING })
                    return@forEach
                }
                var expanded by remember(control.kind) { mutableStateOf(false) }
                Box {
                    AssistChip(onClick = feedbackClick { expanded = true }, enabled = enabled,
                        label = { Text(control.options.firstOrNull { it.action == control.selected }?.label ?: control.title) },
                        leadingIcon = { Icon(when (control.kind) { ChatControlKind.ACCESS -> accessIcon(control.options.firstOrNull { it.action == control.selected }?.value); ChatControlKind.PROVIDER -> Icons.Default.Cloud; else -> Icons.Default.AutoAwesome }, null, Modifier.size(18.dp)) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp)) },
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
}
private fun accessIcon(value: String?) = when (value) {
    "full" -> Icons.Default.GppMaybe
    "assist" -> Icons.Default.VerifiedUser
    else -> Icons.Default.BackHand
}
