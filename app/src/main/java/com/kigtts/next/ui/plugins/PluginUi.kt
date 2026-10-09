@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.kigtts.next.ui.plugins

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.*
import com.kigtts.next.ui.management.ManagedItemList

@Composable
fun SlotActions(slot: UiSlot, registry: PluginRegistry, onAction: (RegisteredAction) -> Unit, modifier: Modifier = Modifier,
    context: PluginContext = PluginContext("", ""), revision: Int = 0, visible: Boolean = true) {
    val actions = remember(slot, registry, context, revision, visible) { if (visible) registry.actions(slot, context) else emptyList() }
    val end = slot in listOf(UiSlot.HEADER_END, UiSlot.EDITOR_TOP_END, UiSlot.EDITOR_BOTTOM_END,
        UiSlot.LISTENING_BOTTOM_END, UiSlot.QUICK_PANEL_END, UiSlot.TAB_END)
    if (slot == UiSlot.HEADER_START || slot == UiSlot.HEADER_END) {
        ElasticButtonGroup(actions.map { ElasticAction(it.key, it.action.label, it.action.icon.vector(), { onAction(it) },
            iconOnly = it.action.buttonStyle == UiButtonStyle.ICON) }, modifier.widthIn(max = 144.dp), plain = true,
            alignEnd = end, overflowTag = slot.name + "-more")
        return
    }
    if (slot !in listOf(UiSlot.HEADER_START, UiSlot.HEADER_END, UiSlot.TAB_START, UiSlot.TAB_END)) {
        ElasticButtonGroup(actions.map { ElasticAction(it.key, it.action.label, it.action.icon.vector(), { onAction(it) },
            iconOnly = it.action.buttonStyle == UiButtonStyle.ICON) }, modifier.widthIn(max = 144.dp),
            alignEnd = end, overflowTag = slot.name + "-more")
        return
    }
    PluginTabActions(actions, onAction, modifier, end, slot.name + "-more")
}

@Composable
fun PluginPageScreen(page: PluginPage?, busy: Boolean = false, resolve: (PluginResource) -> java.io.File? = { null }, invoke: (String, Map<String, String>) -> Unit) {
    if (page == null) { Text("插件页面暂不可用", Modifier.padding(24.dp)); return }
    page.management?.let { list ->
        ManagedItemList(list, busy, resolve, invoke) { page.blocks.forEach { RenderBlock(it) { action -> invoke(action, emptyMap()) } } }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("正在处理插件任务…") }
        page.blocks.forEach { RenderBlock(it) { action -> invoke(action, emptyMap()) } }
    }
}

@Composable
private fun RenderBlock(block: PageBlock, invoke: (String) -> Unit) {
    when (block) {
        is PageBlock.Text -> Text(block.text, style = if (block.heading) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyLarge)
        is PageBlock.Notice -> Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium) {
            Text(block.text, Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
        is PageBlock.Actions -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            block.actions.forEach { action ->
                FilledTonalButton(onClick = feedbackClick { invoke(action.id) }, shapes = expressivePillShapes()) {
                    Icon(action.icon.vector(), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(action.label)
                }
            }
        }
        is PageBlock.Group -> Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(block.title, style = MaterialTheme.typography.titleMedium)
                block.children.forEach { RenderBlock(it, invoke) }
            }
        }
    }
}
