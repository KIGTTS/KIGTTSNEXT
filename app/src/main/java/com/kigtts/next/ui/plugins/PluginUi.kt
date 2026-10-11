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
import com.kigtts.next.ui.navigation.LocalPageToolbarActions
import com.kigtts.next.ui.navigation.RegisterPageActions
import com.kigtts.next.ui.navigation.ToolbarAction
import com.kigtts.next.ui.navigation.RegisterPageLoading
import androidx.compose.foundation.interaction.MutableInteractionSource

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
    RegisterPageLoading(busy)
    if (page == null) { PageContentMotion(busy) { loading -> Text(if(loading)"正在加载插件页面…" else "插件页面暂不可用", Modifier.padding(24.dp)) }; return }
    val toolbar=LocalPageToolbarActions.current!=null
    if(toolbar)RegisterPageActions(page.blocks.filterIsInstance<PageBlock.Actions>().flatMap { it.actions }.map { action ->
        ToolbarAction("plugin-page/"+action.id,action.label,action.icon.vector(),!busy) { invoke(action.id,emptyMap()) }
    })
    val blocks=if(toolbar)page.blocks.filterNot { it is PageBlock.Actions } else page.blocks
    page.management?.let { list ->
        ManagedItemList(list, busy, resolve, invoke) { PageContentMotion(blocks) { shown -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            shown.forEach { RenderBlock(it, !busy) { action -> invoke(action, emptyMap()) } }
        } } }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        PageContentMotion(blocks) { shown -> Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            shown.forEach { RenderBlock(it, !busy) { action -> invoke(action, emptyMap()) } }
        } }
    }
}

@Composable
private fun RenderBlock(block: PageBlock, enabled: Boolean, invoke: (String) -> Unit) {
    when (block) {
        is PageBlock.Text -> Text(block.text, style = if (block.heading) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyLarge)
        is PageBlock.Notice -> Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium) {
            Text(block.text, Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
        is PageBlock.Actions -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            block.actions.forEach { action ->
                val source=remember(action.id) { MutableInteractionSource() }
                val press=rememberImmediateButtonPress(source)
                LaunchedEffect(enabled) { if (!enabled) press.cancel() }
                FilledTonalButton(onClick = feedbackClick { press.click();invoke(action.id) },
                    enabled=enabled, colors=ButtonDefaults.filledTonalButtonColors().animateEnabled(enabled),
                    modifier=if(enabled)Modifier.immediateButtonPress(press) else Modifier,interactionSource=press.native,shapes = expressivePillShapes()) {
                    Icon(action.icon.vector(), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(action.label)
                }
            }
        }
        is PageBlock.Group -> Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(block.title, style = MaterialTheme.typography.titleMedium)
                block.children.forEach { RenderBlock(it, enabled, invoke) }
            }
        }
    }
}
