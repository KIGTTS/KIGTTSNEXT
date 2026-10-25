package com.kigtts.next.ui.cards.camera

import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.math.abs

/** A range is derived from the original selection, so reversing a stroke restores crossed items. */
internal class BurstSelectionRange(val anchor: Int, private val before: Set<Int>) {
    private val adding = anchor !in before
    fun at(index: Int): List<Int> {
        val range = minOf(anchor, index)..maxOf(anchor, index)
        return (if (adding) before + range else before - range.toSet()).sorted()
    }
}

@Composable
internal fun Modifier.burstRangeSelection(grid: LazyGridState, enabled: Boolean, selected: List<Int>,
    change: (List<Int>) -> Unit): Modifier {
    val current = rememberUpdatedState(selected)
    val update = rememberUpdatedState(change)
    val interactive = rememberUpdatedState(enabled)
    val haptic = LocalHapticFeedback.current
    val edge = with(LocalDensity.current) { 64.dp.toPx() }
    val speed = with(LocalDensity.current) { 720.dp.toPx() }
    var pointer by remember(grid) { mutableStateOf<Offset?>(null) }
    var range by remember(grid) { mutableStateOf<BurstSelectionRange?>(null) }
    fun indexAt(position: Offset): Int? {
        val items = grid.layoutInfo.visibleItemsInfo
        val column = items.filter { position.x >= it.offset.x && position.x <= it.offset.x + it.size.width }
        return column.minByOrNull {
            abs(position.y - (it.offset.y + it.size.height / 2f))
        }?.index
    }
    fun edgeFactor(point: Offset): Float {
        val height = grid.layoutInfo.viewportSize.height.toFloat()
        return when {
            point.y < edge && grid.canScrollBackward -> -((edge - point.y) / edge).coerceIn(0f, 1f)
            point.y > height - edge && grid.canScrollForward -> ((point.y - height + edge) / edge).coerceIn(0f, 1f)
            else -> 0f
        }
    }
    LaunchedEffect(grid, enabled, range, pointer) {
        if (range == null || !enabled || pointer?.let(::edgeFactor) == 0f) return@LaunchedEffect
        grid.scroll(MutatePriority.UserInput) {
        var previous = withFrameNanos { it }
        while (isActive && range != null) {
            val now = withFrameNanos { it }
            val seconds = ((now - previous) / 1_000_000_000f).coerceAtMost(.05f); previous = now
            val point = pointer ?: continue
            val factor = edgeFactor(point)
            if (factor == 0f) break
            if (factor != 0f) {
                scrollBy(factor * speed * seconds)
                indexAt(point)?.let { index -> range?.let { update.value(it.at(index)) } }
            }
        }
        }
    }
    return pointerInput(grid) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!interactive.value) return@awaitEachGesture
            val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
            val anchor = indexAt(held.position) ?: return@awaitEachGesture
            held.consume()
            range = BurstSelectionRange(anchor, current.value.toSet()); pointer = held.position
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            update.value(requireNotNull(range).at(anchor))
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val finger = event.changes.firstOrNull { it.id == down.id } ?: break
                    pointer = finger.position
                    if (interactive.value) indexAt(finger.position)?.let { index -> range?.let { update.value(it.at(index)) } }
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed } && interactive.value)
            } finally { range = null; pointer = null }
        }
    }
}
