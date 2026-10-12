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
import com.kigtts.next.ui.settings.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight

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
        ToolbarAction("plugin-page/"+action.id,action.label,action.icon.vector(),!busy, preserveColorWhenLocked=true) { invoke(action.id,emptyMap()) }
    })
    val blocks=page.blocks.filterNot { block ->
        (toolbar && block is PageBlock.Actions) ||
            (block is PageBlock.Text && block.heading && block.text.trim() == page.title.trim())
    }
    page.management?.let { list ->
        if (list.usesSettingsLayout()) PluginSettingsList(list, busy, invoke, blocks.isNotEmpty()) { PluginBlocks(blocks,!busy) { invoke(it,emptyMap()) } }
        else ManagedItemList(list, busy, resolve, invoke) { PluginBlocks(blocks,!busy) { invoke(it,emptyMap()) } }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        PluginBlocks(blocks,!busy) { invoke(it,emptyMap()) }
    }
}

@Composable
private fun PluginBlocks(blocks:List<PageBlock>,enabled:Boolean,invoke:(String)->Unit) {
    MotionColumn(blocks,{ block,index->when(block) {
        is PageBlock.Group->"group/"+block.title
        is PageBlock.Actions->"actions/$index"
        is PageBlock.Text->"text/$index"
        is PageBlock.Notice->"notice/$index"
    } },enabled=enabled&&com.kigtts.next.ui.navigation.LocalPageActive.current,animateInitial=false) { block,active->RenderBlock(block,active,invoke) }
}

@Composable
private fun RenderBlock(block: PageBlock, enabled: Boolean, invoke: (String) -> Unit) {
    when (block) {
        is PageBlock.Text -> PageContentMotion(block.text) { text -> Text(text, style = if (block.heading) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodySmall) }
        is PageBlock.Notice -> Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large) {
            PageContentMotion(block.text) { Text(it, Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        is PageBlock.Actions -> MotionColumn(block.actions,{ action,_->action.id },enabled=enabled,spacing=2.dp,animateInitial=false) { action,interactive ->
            SettingItem(action.label, "", action.icon.vector(), settingPosition(block.actions.indexOf(action), block.actions.size),
                onClick = { invoke(action.id) }, enabled = interactive, trailing = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(24.dp))
                })
        }
        is PageBlock.Group -> SettingsGroup(block.title) {
            PluginBlocks(block.children,enabled,invoke)
        }
    }
}
