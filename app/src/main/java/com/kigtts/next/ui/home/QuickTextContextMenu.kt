package com.kigtts.next.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.reorder.ReorderTarget

internal data class QuickTextMenuTarget(val key: String, val bounds: Rect, val group: Boolean = false)

/** One shared menu for the editor and the opt-in quick editor. */
@Composable
internal fun QuickTextContextMenu(target: QuickTextMenuTarget?, model: AppViewModel,
    dismiss: () -> Unit, editGroup: (String) -> Unit) {
    var deleting by remember { mutableStateOf<QuickTextMenuTarget?>(null) }
    val density = LocalDensity.current
    val view = LocalView.current
    target?.let { selected ->
        val gap = with(density) { 8.dp.roundToPx() }
        val position = remember(selected, gap, view) { object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val origin = IntArray(2).also(view::getLocationInWindow)
                val bounds = selected.bounds
                return IntOffset((origin[0] + bounds.center.x.toInt() - popupContentSize.width / 2)
                    .coerceIn(gap, maxOf(gap, windowSize.width - popupContentSize.width - gap)),
                    (origin[1] + bounds.top.toInt() - popupContentSize.height - gap).let { above ->
                        if (above >= gap) above else (origin[1] + bounds.bottom.toInt() + gap)
                            .coerceAtMost(maxOf(gap, windowSize.height - popupContentSize.height - gap)) })
            }
        } }
        Popup(position, dismiss, PopupProperties(focusable = true)) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp, shadowElevation = 6.dp, modifier = Modifier.testTag("quick-text-context-menu")) {
                Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                    IconButton(onClick = feedbackClick { dismiss(); model.sharing.shareQuickText(selected.key, selected.group) }) {
                        Icon(Icons.Default.Share, "分享", Modifier.size(22.dp))
                    }
                    IconButton(onClick = feedbackClick {
                        dismiss()
                        if (selected.group) editGroup(selected.key)
                        else model.phrases.value.firstOrNull { it.id == selected.key }?.let { model.editPhraseDetails(it, it.category) }
                    }) { Icon(Icons.Default.Edit, "编辑", Modifier.size(22.dp)) }
                    IconButton(onClick = feedbackClick { deleting = selected; dismiss() },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Default.Delete, "删除", Modifier.size(22.dp))
                    }
                }
            }
        }
    }
    deleting?.let { item ->
        MotionAlertDialog(onDismissRequest = { deleting = null }, title = { Text(if (item.group) "删除这个分组？" else "删除这条快捷文本？") },
            text = { Text(if (item.group) "分组中的快捷文本也会删除。" else "删除后无法撤销。") },
            confirmButton = { TextButton(onClick = feedbackClick {
                if (item.group) {
                    if (!model.quickTexts.removeCategory(item.key)) model.message("请至少保留一个分组")
                    if (model.state.value.selectedCategory !in model.categories.value) model.category(model.categories.value.first())
                } else model.quickTexts.remove(item.key)
                deleting = null
            }) { Text("删除") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } })
    }
}

internal fun ReorderTarget.menuTarget(group: Boolean = false) = QuickTextMenuTarget(key, bounds, group)
