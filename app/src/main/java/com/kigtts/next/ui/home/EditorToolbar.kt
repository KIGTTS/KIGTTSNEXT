package com.kigtts.next.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.*

@Composable
fun EditorToolbar(state: AppState, model: AppViewModel) {
    var menu by remember { mutableStateOf(false) }
    var fontBar by remember { mutableStateOf(false) }
    val context = model.context()
    val registeredStart = remember(context, state.pluginRevision) { cornerActions(UiSlot.EDITOR_TOP_START, model) }
    val registeredEnd = remember(context, state.pluginRevision) { cornerActions(UiSlot.EDITOR_TOP_END, model) }
    val start = listOf(ElasticAction("editor-menu", "文本菜单", Icons.Default.MoreVert, { menu = true }),
        ElasticAction("editor-flip", "上下翻转字幕", Icons.Default.SwapVert, model::flipSubtitle)) + registeredStart
    val end = listOf(ElasticAction("editor-font-size", "调整字幕字号", Icons.Default.ZoomIn, { fontBar = !fontBar })) + registeredEnd
    Box {
        Row(Modifier.fillMaxWidth()) {
            if (fontBar) InlineFontBar("字幕字号", state.subtitleSizePercent, SubtitleSize.range, model::setSubtitleSizePercent,
                { fontBar = false }, Modifier.weight(.68f),
                valueDescription = "单字宽度占屏幕 %.1f%%".format(state.subtitleSizePercent))
            else ElasticButtonGroup(start, Modifier.weight(.68f), overflowTag = "editor-top-start-more", overflowLabel = "字幕左上更多操作")
            Spacer(Modifier.weight(.02f))
            ElasticButtonGroup(end, Modifier.weight(.3f), alignEnd = true,
                overflowTag = "editor-top-end-more", overflowLabel = "字幕右上更多操作")
        }
        androidx.activity.compose.BackHandler(menu) { menu = false }
        DropdownMenu(menu, { menu = false }, properties = androidx.compose.ui.window.PopupProperties(focusable = false)) {
            DropdownMenuItem(text = { Text(if (state.streaming) "✓ 流式识别" else "流式识别") },
                onClick = feedbackClick { model.streaming(true); menu = false })
            DropdownMenuItem(text = { Text(if (!state.streaming) "✓ 完整录音识别（最多 8 秒）" else "完整录音识别（最多 8 秒）") },
                onClick = feedbackClick { model.streaming(false); menu = false })
            model.registry.actions(UiSlot.EDITOR_MENU, context).forEach { action ->
                DropdownMenuItem(text = { Text(action.action.label) }, leadingIcon = { Icon(action.action.icon.vector(), null) },
                    onClick = feedbackClick { model.invoke(action); menu = false })
            }
        }
    }
}

fun cornerActions(slot: UiSlot, model: AppViewModel) = model.registry.actions(slot, model.context()).map { registered ->
    ElasticAction(registered.key, registered.action.label, registered.action.icon.vector(),
        { model.invoke(registered) }, iconOnly = registered.action.buttonStyle == UiButtonStyle.ICON)
}
