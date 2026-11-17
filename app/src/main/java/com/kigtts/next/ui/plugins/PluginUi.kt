@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.kigtts.next.ui.plugins

import com.kigtts.next.i18n.uiText
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
        ElasticButtonGroup(actions.map { ElasticAction(it.key, it.action.label, it.action.icon.vector(it.action.symbol), { onAction(it) },
            iconOnly = it.action.buttonStyle == UiButtonStyle.ICON, busy = it.action.busy, badge = it.action.badge) }, modifier.widthIn(max = 144.dp), plain = true,
            alignEnd = end, overflowTag = slot.name + "-more")
        return
    }
    if (slot !in listOf(UiSlot.HEADER_START, UiSlot.HEADER_END, UiSlot.TAB_START, UiSlot.TAB_END)) {
        ElasticButtonGroup(actions.map { ElasticAction(it.key, it.action.label, it.action.icon.vector(it.action.symbol), { onAction(it) },
            iconOnly = it.action.buttonStyle == UiButtonStyle.ICON, busy = it.action.busy, badge = it.action.badge) }, modifier.widthIn(max = 144.dp),
            alignEnd = end, overflowTag = slot.name + "-more")
        return
    }
    PluginTabActions(actions, onAction, modifier, end, slot.name + "-more")
}

@Composable
fun PluginPageScreen(page: PluginPage?, busy: Boolean = false, resolve: (PluginResource) -> java.io.File? = { null }, browser: (@Composable (PageBlock.Browser) -> Unit)? = null, historyCache: ChatHistoryCache? = null, historyKey: String = "", onBack: () -> Unit = {}, extraSettings: (@Composable () -> Unit)? = null,
    startVoiceInput: (() -> (suspend (Boolean) -> String?)?)? = null, followIme: Boolean = false,
    invoke: (String, Map<String, String>) -> Unit) {
    page?.search?.let { search ->
        PluginSearchScreen(search, onBack, invoke) {
            PluginPageScreen(page.copy(search = null), busy, resolve, browser, historyCache, historyKey, onBack, extraSettings,
                startVoiceInput, followIme, invoke)
        }
        return
    }
    PluginPageEntry(page, busy, invoke)
    RegisterPageLoading(busy || page?.loading == true)
    if (page == null) { PageContentMotion(busy) { loading -> Text(if(loading)uiText("plugin.page.loading") else uiText("plugin.page.unavailable"), Modifier.padding(24.dp)) }; return }
    val toolbar=LocalPageToolbarActions.current!=null
    val actionGroups = page.blocks.filterIsInstance<PageBlock.Actions>()
    if(toolbar)RegisterPageActions(actionGroups.flatMap { it.actions }.map { action ->
        ToolbarAction("plugin-page/"+action.id,action.label,action.icon.vector(action.symbol),!busy, preserveColorWhenLocked=true, badge=action.badge) { invoke(action.id,emptyMap()) }
    }, primaryCount = if (page.blocks.any { it is PageBlock.Chat } || actionGroups.firstOrNull()?.actions?.size == 3) 3 else 2)
    val blocks=page.blocks.filterNot { block ->
        (toolbar && block is PageBlock.Actions) ||
            (block is PageBlock.Text && block.heading && block.text.trim() == page.title.trim())
    }
    blocks.filterIsInstance<PageBlock.Browser>().firstOrNull()?.let { block ->
        if (browser != null) browser(block) else Text(uiText("plugin.browser.unavailable"), Modifier.padding(24.dp))
        return
    }
    blocks.filterIsInstance<PageBlock.SourceEditor>().firstOrNull()?.let { editor ->
        PluginSourceEditorScreen(editor, !busy, invoke)
        return
    }
    blocks.filterIsInstance<PageBlock.Chat>().firstOrNull()?.let { chat ->
        PluginChatScreen(chat, !busy, invoke, resolve, historyCache, historyKey + chat.conversationId, startVoiceInput, followIme) { PluginBlocks(blocks.filterNot { it is PageBlock.Chat }, !busy, invoke) }
        return
    }
    blocks.filterIsInstance<PageBlock.Files>().firstOrNull()?.let { files ->
        PluginFilesScreen(files, !busy, invoke)
        return
    }
    PluginPageRefresh(page, busy, invoke) {
        val list = page.management
        when {
            list?.style == ManagementListStyle.CATALOG -> PluginCatalogList(list, busy, resolve, invoke) { PluginPageHeader(page.header, resolve, page.headerAction, !busy, invoke); PluginBlocks(blocks,!busy,invoke, resolve = resolve) }
            list != null && list.usesSettingsLayout() -> PluginSettingsList(list, busy, invoke, blocks.isNotEmpty() || page.header != null, resolve) { PluginPageHeader(page.header, resolve, page.headerAction, !busy, invoke); PluginBlocks(blocks,!busy,invoke, resolve = resolve) }
            list != null -> ManagedItemList(list, busy, resolve, invoke) { PluginPageHeader(page.header, resolve, page.headerAction, !busy, invoke); extraSettings?.invoke(); PluginBlocks(blocks,!busy,invoke, resolve = resolve) }
            else -> ReadablePage(840.dp) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(com.kigtts.next.ui.navigation.pageContentPadding()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                PluginPageHeader(page.header, resolve, page.headerAction, !busy, invoke)
                extraSettings?.invoke()
                PluginBlocks(blocks,!busy,invoke, resolve = resolve)
            } }
        }
    }
}

@Composable
internal fun PluginBlocks(blocks:List<PageBlock>,enabled:Boolean,invoke:(String, Map<String,String>)->Unit, grouped:Boolean = false, resolve: (PluginResource) -> java.io.File? = { null }) {
    val pageActive = com.kigtts.next.ui.navigation.LocalPageActive.current
    MotionColumn(blocks,{ block,index->when(block) {
        is PageBlock.Group->"group/"+block.title
        is PageBlock.Actions->"actions/$index"
        is PageBlock.Text->"text/$index"
        is PageBlock.Markdown -> "markdown/$index"
        is PageBlock.Gallery -> "gallery/$index"
        is PageBlock.StrokeGuide -> "stroke/"+block.title
        is PageBlock.Notice->"notice/$index"
        is PageBlock.Toggle -> "toggle/"+block.actionId
        is PageBlock.Choice -> "choice/"+block.title
        is PageBlock.Select -> "select/"+block.title
        is PageBlock.Input -> "input/"+block.actionId
        is PageBlock.Slider -> "slider/"+block.control.actionId
        is PageBlock.Chat -> "chat/"+block.sendAction
        is PageBlock.Files -> "files/"+block.openAction
        is PageBlock.SourceEditor -> "source/"+block.id
        is PageBlock.Browser -> "browser/$index"
    } },enabled=true,animateInitial=false,
        spacing = if (grouped) 2.dp else 12.dp) { block,active ->
        RenderBlock(block, true, { action, inputs -> if (active && enabled && pageActive) invoke(action, inputs) },
            if (grouped) settingPosition(blocks.indexOf(block).coerceAtLeast(0), blocks.size) else SettingPosition.SINGLE, resolve, grouped)
    }
}

@Composable
private fun RenderBlock(block: PageBlock, enabled: Boolean, invoke: (String, Map<String,String>) -> Unit, position:SettingPosition, resolve: (PluginResource) -> java.io.File?, grouped: Boolean = false) {
    if (grouped && (block is PageBlock.Text || block is PageBlock.Markdown || block is PageBlock.Gallery)) {
        Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = groupedSettingShape(position, MaterialTheme.shapes.small)) {
            Box(Modifier.padding(16.dp)) { RenderBlock(block, enabled, invoke, position, resolve) }
        }
        return
    }
    when (block) {
        is PageBlock.StrokeGuide -> PluginStrokeGuide(block, enabled, invoke, position)
        is PageBlock.Slider -> Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = groupedSettingShape(position, MaterialTheme.shapes.small)) {
            PluginSliderSetting(block.control, Modifier.padding(16.dp), enabled, continuous = false) {
                invoke(block.control.actionId, mapOf("value" to it))
            }
        }
        is PageBlock.Text -> PageContentMotion(block.text) { text -> Text(text, style = if (block.heading) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodySmall) }
        is PageBlock.Markdown -> PluginMarkdown(block, resolve)
        is PageBlock.Gallery -> PluginGallery(block, resolve)
        is PageBlock.Notice -> Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large) {
            PageContentMotion(block.text) { Text(it, Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        is PageBlock.Actions -> MotionColumn(block.actions,{ action,_->action.id },enabled=enabled,spacing=2.dp,animateInitial=false) { action,interactive ->
            SettingItem(action.label, "", (if(action.icon == PluginIcon.NONE && action.symbol == null) null else action.icon.vector(action.symbol)), settingPosition(block.actions.indexOf(action), block.actions.size),
                onClick = { invoke(action.id, emptyMap()) }, enabled = interactive, badge = action.badge, trailing = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(24.dp))
                })
        }
        is PageBlock.Group -> if (block.collapsible) PluginCollapsibleGroup(block) {
            PluginBlocks(block.children,enabled,invoke, grouped = true, resolve = resolve)
        } else SettingsGroup(block.title) { PluginBlocks(block.children,enabled,invoke, grouped = true, resolve = resolve) }
        is PageBlock.Toggle -> ToggleSetting(block.title, block.summary, (if(block.icon == PluginIcon.NONE && block.symbol == null) null else block.icon.vector(block.symbol)), block.checked,
            { invoke(block.actionId, mapOf("checked" to it.toString())) }, enabled = enabled, position = position, preserveColorWhenLocked = true)
        is PageBlock.Input -> SettingItem(block.title, if (block.secret) {
            if (block.value.isBlank()) "未设置" else "已设置"
        } else block.value.lineSequence().firstOrNull().orEmpty().take(120), (if(block.icon == PluginIcon.NONE && block.symbol == null) null else block.icon.vector(block.symbol)), position, enabled = enabled,
            onClick = { invoke(block.actionId, emptyMap()) }, trailing = {
                block.trailingAction?.let { action ->
                    IconButton(onClick = feedbackClick { invoke(action.id, emptyMap()) }, enabled = enabled) {
                        Icon(action.icon.vector(action.symbol), action.label, Modifier.size(24.dp))
                    }
                } ?: Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,null)
            })
        is PageBlock.Select -> PluginSelect(block, enabled, position, invoke)
        is PageBlock.Choice -> SettingItem(block.title, "", (if(block.icon == PluginIcon.NONE && block.symbol == null) null else block.icon.vector(block.symbol)), position) {
            ChoiceButtonGroup(block.title, block.options.map { it.id }, block.selected,
                { id -> block.options.first { it.id == id }.label }, { invoke(it, emptyMap()) }, enabled = enabled, preserveColorWhenLocked = true)
        }
        is PageBlock.Chat, is PageBlock.Files, is PageBlock.SourceEditor, is PageBlock.Browser -> Unit // Full-page content owns its own scroll/IME viewport.
    }
}
