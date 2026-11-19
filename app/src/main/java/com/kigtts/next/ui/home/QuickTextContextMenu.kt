package com.kigtts.next.ui.home

import com.kigtts.next.i18n.uiText
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
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
    var displayed by remember { mutableStateOf<QuickTextMenuTarget?>(null) }
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(target) {
        if (target != null) displayed = target
        reveal.animateTo(if (target != null) 1f else 0f, tween(if (target != null) 160 else 120))
        if (target == null) displayed = null
    }
    displayed?.let { selected ->
        val gap = with(density) { 8.dp.roundToPx() }
        val shadowPadding = 16.dp
        val inset = with(density) { shadowPadding.roundToPx() }
        val position = remember(selected, gap, inset, view) { object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val origin = IntArray(2).also(view::getLocationInWindow)
                val bounds = selected.bounds
                return IntOffset((origin[0] + bounds.center.x.toInt() - popupContentSize.width / 2)
                    .coerceIn(gap, maxOf(gap, windowSize.width - popupContentSize.width - gap)),
                    (origin[1] + bounds.top.toInt() - popupContentSize.height - gap + inset).let { above ->
                        if (above >= gap) above else (origin[1] + bounds.bottom.toInt() + gap)
                            .minus(inset)
                            .coerceAtMost(maxOf(gap, windowSize.height - popupContentSize.height - gap)) })
            }
        } }
        Popup(position, dismiss, PopupProperties(focusable = true)) {
            Box(Modifier.graphicsLayer {
                alpha = reveal.value
                compositingStrategy = CompositingStrategy.Offscreen
                scaleX = .94f + .06f * reveal.value; scaleY = scaleX
                translationY = with(density) { 6.dp.toPx() } * (1f - reveal.value)
            }.padding(shadowPadding)) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp, shadowElevation = 6.dp, modifier = Modifier.testTag("quick-text-context-menu")) {
                Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                    IconButton(enabled = target != null, onClick = feedbackClick { dismiss(); model.sharing.shareQuickText(selected.key, selected.group) }) {
                        Icon(Icons.Default.Share, uiText("action.share"), Modifier.size(22.dp))
                    }
                    IconButton(enabled = target != null, onClick = feedbackClick {
                        dismiss()
                        if (selected.group) editGroup(selected.key)
                        else model.phrases.value.firstOrNull { it.id == selected.key }?.let { model.editPhraseDetails(it, it.category) }
                    }) { Icon(Icons.Default.Edit, uiText("action.edit"), Modifier.size(22.dp)) }
                    IconButton(enabled = target != null, onClick = feedbackClick { deleting = selected; dismiss() },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Default.Delete, uiText("action.delete"), Modifier.size(22.dp))
                    }
                }
            }
            }
        }
    }
    deleting?.let { item ->
        MotionAlertDialog(onDismissRequest = { deleting = null }, title = { Text(if (item.group) uiText("quick.delete_group") else uiText("quick.delete_entry")) },
            text = { Text(if (item.group) uiText("quick.delete_group_body") else uiText("quick.delete_entry_body")) },
            confirmButton = { TextButton(onClick = feedbackClick {
                if (item.group) {
                    if (!model.quickTexts.removeCategory(item.key)) model.message(model.hostCopy("quick.keep_one_group"))
                    if (model.state.value.selectedCategory !in model.categories.value) model.category(model.categories.value.first())
                } else model.quickTexts.remove(item.key)
                deleting = null
            }) { Text(uiText("action.delete")) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(uiText("action.cancel")) } })
    }
}

internal fun ReorderTarget.menuTarget(group: Boolean = false) = QuickTextMenuTarget(key, bounds, group)
