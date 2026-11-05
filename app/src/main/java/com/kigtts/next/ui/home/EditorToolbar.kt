package com.kigtts.next.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.*
import com.kigtts.next.media.*

@Composable
fun EditorToolbar(state: AppState, model: AppViewModel) {
    var menu by remember { mutableStateOf(false) }
    var alignmentDialog by remember { mutableStateOf(false) }
    var fontBar by remember { mutableStateOf(false) }
    val context = model.context()
    val registeredStart = remember(context, state.pluginRevision) { cornerActions(UiSlot.EDITOR_TOP_START, model) }
    val registeredEnd = remember(context, state.pluginRevision) { cornerActions(UiSlot.EDITOR_TOP_END, model) }
    val start = listOf(ElasticAction("editor-menu", "文本菜单", Icons.Default.MoreVert, { menu = true }),
        ElasticAction("editor-flip", "上下翻转字幕", Icons.Default.SwapVert, model::flipSubtitle)) + registeredStart
    LaunchedEffect(state.mediaId) { if (state.mediaId != null) fontBar = false }
    val end = (if (state.mediaId == null) listOf(ElasticAction("editor-font-size", "调整字幕字号", Icons.Default.ZoomIn, { fontBar = !fontBar })) else emptyList()) + registeredEnd
    Box {
        Row(Modifier.fillMaxWidth()) {
            FontToolbarSwap(fontBar, "字幕字号", Modifier.weight(.68f)) { expanded ->
            if (expanded) InlineFontBar("字幕字号", state.subtitleSizePercent, SubtitleSize.range, model::setSubtitleSizePercent,
                { fontBar = false }, Modifier.fillMaxWidth(),
                valueDescription = "单字宽度占屏幕 %.1f%%".format(state.subtitleSizePercent))
            else ElasticButtonGroup(start, Modifier.fillMaxWidth(), overflowTag = "editor-top-start-more", overflowLabel = "字幕左上更多操作", stableItems = true)
            }
            Spacer(Modifier.weight(.02f))
            ElasticButtonGroup(end, Modifier.weight(.3f), alignEnd = true,
                overflowTag = "editor-top-end-more", overflowLabel = "字幕右上更多操作", stableItems = true)
        }
        androidx.activity.compose.BackHandler(menu) { menu = false }
        DropdownMenu(menu, { menu = false }, properties = androidx.compose.ui.window.PopupProperties(focusable = false)) {
            val media = model.mediaRepository.get(state.mediaId)
            if (state.mediaId == null || media?.kind == MediaKind.AUDIO) {
                DropdownMenuItem(text = { Text("添加到快捷文本") },
                    leadingIcon = { Icon(Icons.Default.Add, null) },
                    enabled = state.editorText.isNotBlank(),
                    onClick = feedbackClick { menu = false; model.addEditorToQuickText() },
                    modifier = Modifier.testTag("subtitle-add-quick-text"))
            }
            if (media?.kind in setOf(MediaKind.IMAGE, MediaKind.MOTION_PHOTO, MediaKind.VIDEO)) MediaScale.entries.forEach { mode ->
                DropdownMenuItem(text = { Text(mode.label) }, leadingIcon = { if (media?.scale == mode) Icon(Icons.Default.Check, null) },
                    onClick = feedbackClick { model.mediaScale(mode); menu = false }, modifier = Modifier.testTag("media-scale-" + mode.name.lowercase()))
            }
            val appearance = state.subtitleAppearance
            DropdownMenuItem(text = { Text(if (appearance.bold) "关闭粗体" else "打开粗体") },
                leadingIcon = { Icon(Icons.Default.FormatBold, null) },
                trailingIcon = { if (appearance.bold) Icon(Icons.Default.Check, null) },
                onClick = feedbackClick { model.setSubtitleAppearance(appearance.copy(bold = !appearance.bold)); menu = false },
                modifier = Modifier.testTag("subtitle-bold"))
            DropdownMenuItem(text = { Text("文本对齐") },
                leadingIcon = { Icon(Icons.Default.FormatAlignCenter, null) },
                trailingIcon = { Text(appearance.alignment.label, style = MaterialTheme.typography.labelMedium) },
                onClick = feedbackClick { menu = false; alignmentDialog = true }, modifier = Modifier.testTag("subtitle-alignment"))
            DropdownMenuItem(text = { Text(if (appearance.richTextEditing) "关闭富文本编辑" else "开启富文本编辑") },
                leadingIcon = { Icon(Icons.Default.FormatPaint, null) },
                trailingIcon = { if (appearance.richTextEditing) Icon(Icons.Default.Check, null) },
                onClick = feedbackClick { model.setSubtitleAppearance(appearance.copy(richTextEditing = !appearance.richTextEditing)); menu = false },
                modifier = Modifier.testTag("subtitle-rich-editing"))
            model.registry.actions(UiSlot.EDITOR_MENU, context).forEach { action ->
                DropdownMenuItem(text = { Text(action.action.label) }, leadingIcon = { Icon(action.action.icon.vector(), null) },
                    onClick = feedbackClick { model.invoke(action); menu = false })
            }
        }
        if (alignmentDialog) SubtitleAlignmentDialog(state.subtitleAppearance.alignment,
            { model.setSubtitleAppearance(model.state.value.subtitleAppearance.copy(alignment = it)) }, { alignmentDialog = false })
    }
}

fun cornerActions(slot: UiSlot, model: AppViewModel, owner: String? = null) = model.registry.actions(slot, model.context())
    .filter { owner == null || it.plugin.descriptor.id == owner }.map { registered ->
    ElasticAction(registered.key, registered.action.label, registered.action.icon.vector(registered.action.symbol),
        { model.invoke(registered) }, iconOnly = registered.action.buttonStyle == UiButtonStyle.ICON, busy = registered.action.busy)
}
