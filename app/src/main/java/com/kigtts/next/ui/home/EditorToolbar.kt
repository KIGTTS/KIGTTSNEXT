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
import kotlinx.coroutines.launch

@Composable
fun EditorToolbar(state: AppState, model: AppViewModel) {
    val scope=rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
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
            else ElasticButtonGroup(start, Modifier.fillMaxWidth(), overflowTag = "editor-top-start-more", overflowLabel = "字幕左上更多操作")
            }
            Spacer(Modifier.weight(.02f))
            ElasticButtonGroup(end, Modifier.weight(.3f), alignEnd = true,
                overflowTag = "editor-top-end-more", overflowLabel = "字幕右上更多操作")
        }
        androidx.activity.compose.BackHandler(menu) { menu = false }
        DropdownMenu(menu, { menu = false }, properties = androidx.compose.ui.window.PopupProperties(focusable = false)) {
            val media = model.mediaRepository.get(state.mediaId)
            if (media?.kind in setOf(MediaKind.IMAGE, MediaKind.MOTION_PHOTO, MediaKind.VIDEO)) MediaScale.entries.forEach { mode ->
                DropdownMenuItem(text = { Text(mode.label) }, leadingIcon = { if (media?.scale == mode) Icon(Icons.Default.Check, null) },
                    onClick = feedbackClick { model.mediaScale(mode); menu = false }, modifier = Modifier.testTag("media-scale-" + mode.name.lowercase()))
            }
            DropdownMenuItem(text = { Text("选择图片、视频或网页") }, onClick = feedbackClick { menu = false; model.content.picker() })
            DropdownMenuItem(text = { Text("文本编辑器") }, onClick = feedbackClick { menu = false; model.content.editor() })
            DropdownMenuItem(text = { Text("页面文件") }, onClick = feedbackClick { menu = false; scope.launch { runCatching { model.content.htmlFiles() }.onFailure { model.message(it.message ?: "打开失败") } } })
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
    ElasticAction(registered.key, registered.action.label, registered.action.icon.vector(registered.action.symbol),
        { model.invoke(registered) }, iconOnly = registered.action.buttonStyle == UiButtonStyle.ICON, busy = registered.action.busy)
}
