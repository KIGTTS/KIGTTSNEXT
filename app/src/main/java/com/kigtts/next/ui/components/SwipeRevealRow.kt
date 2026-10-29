@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class RevealAction(val id: String, val label: String, val icon: ImageVector, val destructive: Boolean = false)
private enum class RevealSide { CLOSED, START, END }

/** Reveals actions without ever executing them on release. One owner per list closes sibling rows. */
@Composable internal fun SwipeRevealRow(
    id: String, active: Boolean, onActivate: () -> Unit,
    startActions: List<RevealAction> = emptyList(), endActions: List<RevealAction> = emptyList(),
    enabled: Boolean = true, modifier: Modifier = Modifier, invoke: (String) -> Unit,
    content: @Composable (revealed: Boolean, toggleActions: () -> Unit, closeActions: () -> Unit) -> Unit,
) {
    val state = remember(id) { AnchoredDraggableState(RevealSide.CLOSED) }
    val interaction = remember(id) { MutableInteractionSource() }
    val dragging by interaction.collectIsDraggedAsState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    fun close() { scope.launch { state.animateTo(RevealSide.CLOSED, tween(220)) } }
    LaunchedEffect(dragging) { if (dragging) onActivate() }
    LaunchedEffect(active, enabled) { if (!active || !enabled) state.animateTo(RevealSide.CLOSED, tween(220)) }
    BackHandler(state.targetValue != RevealSide.CLOSED) { close() }
    BoxWithConstraints(modifier.clipToBounds().testTag("reveal-row/$id")) {
        // Keep a part of the row visible, even on a narrow split-screen window.
        val slots = ((maxWidth.value * .8f + 4) / 52).toInt().coerceIn(1, 4)
        val start = revealVisibleActions(startActions, slots)
        val end = revealVisibleActions(endActions, slots)
        val startWidth = with(density) { (start.size * 52).dp.toPx() }
        val endWidth = with(density) { (end.size * 52).dp.toPx() }
        val anchors = remember(startWidth, endWidth) {
            DraggableAnchors {
                RevealSide.CLOSED at 0f
                if (start.isNotEmpty()) RevealSide.START at startWidth
                if (end.isNotEmpty()) RevealSide.END at -endWidth
            }
        }
        SideEffect { state.updateAnchors(anchors) }
        val side by remember(state) { derivedStateOf {
            when { state.offset > .5f -> RevealSide.START; state.offset < -.5f -> RevealSide.END; else -> RevealSide.CLOSED }
        } }
        val revealed = side != RevealSide.CLOSED
        fun act(action: String) { close(); invoke(action) }
        Box(Modifier.matchParentSize().padding(vertical = 4.dp)) {
            if (side == RevealSide.START) RevealRail(id, start, startActions, true, startWidth, { state.offset }, enabled, ::act,
                Modifier.align(Alignment.CenterStart).fillMaxHeight())
            if (side == RevealSide.END) RevealRail(id, end, endActions, false, endWidth, { state.offset }, enabled, ::act,
                Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
        Box(Modifier.fillMaxWidth().offset { IntOffset((state.offset.takeUnless(Float::isNaN) ?: 0f).roundToInt(), 0) }
            .anchoredDraggable(state, Orientation.Horizontal, enabled = enabled && (start.isNotEmpty() || end.isNotEmpty()),
                interactionSource = interaction,
                flingBehavior = AnchoredDraggableDefaults.flingBehavior(state, positionalThreshold = { it * .4f }, animationSpec = tween(220)))) {
            content(revealed, {
                onActivate()
                val target = if (state.targetValue == RevealSide.END) RevealSide.CLOSED
                    else if (end.isNotEmpty()) RevealSide.END else RevealSide.START
                scope.launch { state.animateTo(target, tween(220)) }
            }, ::close)
        }
    }
}

private const val MORE = "\u0000more"
/** A destructive action always stays at the far edge; overflow remains explicitly accessible. */
internal fun revealVisibleActions(actions: List<RevealAction>, slots: Int): List<RevealAction> {
    if (actions.size <= slots) return actions
    if (slots <= 1) return listOf(RevealAction(MORE, "更多操作", Icons.Default.MoreHoriz))
    val delete = actions.lastOrNull { it.destructive }
    val regular = actions.filterNot { it == delete }
    return regular.take((slots - 1 - if (delete != null) 1 else 0).coerceAtLeast(0)) +
        RevealAction(MORE, "更多操作", Icons.Default.MoreHoriz) + listOfNotNull(delete)
}

@Composable private fun RevealRail(id: String, visible: List<RevealAction>, all: List<RevealAction>, start: Boolean,
    width: Float, offset: () -> Float, enabled: Boolean, invoke: (String) -> Unit, modifier: Modifier) {
    var menu by remember(id, start) { mutableStateOf(false) }
    Row(modifier.graphicsLayer { alpha = (abs(offset()) / width.coerceAtLeast(1f)).coerceIn(0f, 1f) },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!start) Spacer(Modifier.width(0.dp))
        visible.forEach { action -> Box(Modifier.width(48.dp).fillMaxHeight()) {
            val colors = if (action.destructive) IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                else IconButtonDefaults.filledTonalIconButtonColors()
            FilledTonalIconButton(onClick = feedbackClick { if (action.id == MORE) menu = true else invoke(action.id) },
                shapes = IconButtonDefaults.shapes(),
                enabled = enabled, colors = colors,
                modifier = Modifier.fillMaxSize().testTag(if (action.id == MORE) "reveal-overflow/$id" else "reveal-action/$id/${action.id}")) {
                Icon(action.icon, action.label, Modifier.size(24.dp))
            }
            if (action.id == MORE) DropdownMenu(menu, { menu = false }) {
                all.filterNot { candidate -> visible.any { it.id == candidate.id } }.forEach { extra ->
                    DropdownMenuItem(text = { Text(extra.label) }, leadingIcon = { Icon(extra.icon, null) },
                        onClick = feedbackClick { menu = false; invoke(extra.id) })
                }
            }
        } }
        if (start) Spacer(Modifier.width(0.dp))
    }
}
