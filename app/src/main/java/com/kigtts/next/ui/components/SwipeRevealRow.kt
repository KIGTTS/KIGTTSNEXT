@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class RevealAction(val id: String, val label: String, val icon: ImageVector, val destructive: Boolean = false)
private enum class RevealSide { CLOSED, START, END }

/** Stable pointer owner: only physical release can commit a full right swipe. */
@Composable internal fun SwipeRevealRow(
    id: String, active: Boolean, onActivate: () -> Unit,
    startActions: List<RevealAction> = emptyList(), endActions: List<RevealAction> = emptyList(),
    enabled: Boolean = true, modifier: Modifier = Modifier, fullSwipeStartAction: String? = null, invoke: (String) -> Unit,
    holdRequest: Int = 0,
    content: @Composable (revealed: Boolean, toggleActions: () -> Unit, closeActions: () -> Unit) -> Unit,
) {
    var offset by remember(id) { mutableFloatStateOf(0f) }
    var target by remember(id) { mutableStateOf(RevealSide.CLOSED) }
    var settling by remember(id) { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val feedback = LocalHapticFeedback.current
    val more = com.kigtts.next.i18n.uiText("overlay.row.more")
    val latestInvoke by rememberUpdatedState(invoke)
    val latestActivate by rememberUpdatedState(onActivate)
    fun settle(side: RevealSide, distance: Float) {
        settling?.cancel(); target = side
        settling = scope.launch { Animatable(offset).animateTo(distance, tween(220)) { offset = value } }
    }
    fun close() = settle(RevealSide.CLOSED, 0f)
    LaunchedEffect(active, enabled) { if (!active || !enabled) close() }
    BackHandler(abs(offset) > .5f) { close() }
    BoxWithConstraints(modifier.clipToBounds().testTag("reveal-row/$id")) {
        val slots = ((maxWidth.value * .8f + 4) / 52).toInt().coerceIn(1, 4)
        val start = remember(startActions, slots, more) { revealVisibleActions(startActions, slots, more) }
        val end = remember(endActions, slots, more) { revealVisibleActions(endActions, slots, more) }
        val startWidth = with(density) { (start.size * 52).dp.toPx() }
        val endWidth = with(density) { (end.size * 52).dp.toPx() }
        val fullWidth = with(density) { (maxWidth - 16.dp).toPx() }.coerceAtLeast(startWidth)
        val triggerWidth = maxOf(startWidth, fullWidth * .92f)
        val side = when { offset > .5f -> RevealSide.START; offset < -.5f -> RevealSide.END; else -> RevealSide.CLOSED }
        LaunchedEffect(startWidth, endWidth) {
            if (target == RevealSide.END) settle(target, -endWidth)
            else if (target == RevealSide.START) settle(target, startWidth)
        }
        LaunchedEffect(holdRequest) {
            if (holdRequest != 0 && enabled && active) {
                if (end.isNotEmpty()) settle(RevealSide.END, -endWidth)
                else if (start.isNotEmpty() && fullSwipeStartAction == null) settle(RevealSide.START, startWidth)
            }
        }
        fun act(action: String) { close(); latestInvoke(action) }
        // The gesture detector stays outside the translated content. Row movement cannot
        // alter the pointer's coordinate system or restart the gesture on every frame.
        Box(Modifier.fillMaxWidth().pointerInput(id, enabled, startWidth, endWidth, fullWidth, fullSwipeStartAction) {
            if (enabled && (start.isNotEmpty() || end.isNotEmpty())) {
                var edge = 0
                detectHorizontalDragGestures(onDragStart = {
                    settling?.cancel(); latestActivate(); edge = 0
                }, onDragEnd = {
                    val pin = fullSwipeStartAction
                    if (pin != null && offset >= triggerWidth) latestInvoke(pin)
                    when {
                        offset < 0f && -offset >= endWidth * .4f -> settle(RevealSide.END, -endWidth)
                        pin == null && offset > 0f && offset >= startWidth * .4f -> settle(RevealSide.START, startWidth)
                        else -> close()
                    }
                }, onDragCancel = { close() }) { change, amount ->
                    change.consume()
                    offset = (offset + amount).coerceIn(-endWidth, if (fullSwipeStartAction != null) fullWidth else startWidth)
                    val next = when {
                        fullSwipeStartAction != null && offset >= triggerWidth -> 1
                        endWidth > 0f && offset <= -endWidth + 1f -> -1
                        else -> 0
                    }
                    if (next != 0 && next != edge) feedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    edge = next
                }
            }
        }) {
            Box(Modifier.matchParentSize()) {
                if (side == RevealSide.START) RevealRail(id, start, startActions, true, startWidth, { offset }, enabled, ::act,
                    Modifier.align(Alignment.CenterStart).fillMaxHeight())
                if (side == RevealSide.END) RevealRail(id, end, endActions, false, endWidth, { offset }, enabled, ::act,
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
            Box(Modifier.fillMaxWidth().offset { IntOffset(offset.roundToInt(), 0) }) {
                content(side != RevealSide.CLOSED, {
                    latestActivate()
                    when {
                        target != RevealSide.CLOSED -> close()
                        end.isNotEmpty() -> settle(RevealSide.END, -endWidth)
                        start.isNotEmpty() -> settle(RevealSide.START, startWidth)
                    }
                }, ::close)
            }
        }
    }
}

private const val MORE = "\u0000more"
/** A destructive action always stays at the far edge; overflow remains explicitly accessible. */
internal fun revealVisibleActions(actions: List<RevealAction>, slots: Int, more: String): List<RevealAction> {
    if (actions.size <= slots) return actions
    if (slots <= 1) return listOf(RevealAction(MORE, more, Icons.Default.MoreHoriz))
    val delete = actions.lastOrNull { it.destructive }
    val regular = actions.filterNot { it == delete }
    return regular.take((slots - 1 - if (delete != null) 1 else 0).coerceAtLeast(0)) +
        RevealAction(MORE, more, Icons.Default.MoreHoriz) + listOfNotNull(delete)
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
