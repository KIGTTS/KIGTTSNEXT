package com.kigtts.next.ui.components.reorder

import androidx.compose.foundation.gestures.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

data class ReorderTarget(val key: String, val bounds: Rect)

@Stable
class ReorderDrag(private val targets: () -> List<ReorderTarget>, private val move: (String, String) -> Unit,
    val horizontalOnly: Boolean = false, private val animateDrop: Boolean = false, val verticalOnly: Boolean = false) {
    var targetProvider: (() -> List<ReorderTarget>)? = null
    var moveProvider: ((String, String) -> Unit)? = null
    var key by mutableStateOf<String?>(null); private set
    var settling by mutableStateOf(false); private set
    var offset by mutableStateOf(Offset.Zero); private set
    var origin = Offset.Zero
    val handles = mutableMapOf<String, Rect>()
    private var initial = Offset.Zero
    private var touch = Offset.Zero
    private var pickedBounds = Rect.Zero
    val pointer get() = touch + offset
    val floatingBounds get() = pickedBounds.translate(offset)
    private fun currentTargets() = targetProvider?.invoke() ?: targets()
    fun hit(point: Offset) = currentTargets().firstOrNull { it.bounds.contains(point) }
    fun targetBounds(id: String) = currentTargets().firstOrNull { it.key == id }?.bounds
    fun begin(item: ReorderTarget, point: Offset) {
        settling = false
        key = item.key; initial = item.bounds.topLeft; touch = point; offset = Offset.Zero; pickedBounds = item.bounds
    }
    fun drag(delta: Offset) { offset += if (horizontalOnly) Offset(delta.x, 0f) else if (verticalOnly) Offset(0f, delta.y) else delta; checkTarget() }
    fun checkTarget() {
        val current = key ?: return
        hit(pointer)?.takeIf { it.key != current }?.let { (moveProvider ?: move)(current, it.key) }
    }
    fun translation(id: String): Offset = if (key != id) Offset.Zero else
        currentTargets().firstOrNull { it.key == id }?.let { initial + offset - it.bounds.topLeft } ?: Offset.Zero
    fun finish() {
        if (animateDrop && key != null) settling = true else key = null
    }
    fun completeDrop(id: String) {
        if (settling && key == id) { key = null; settling = false }
    }
}

@Composable
fun rememberReorderDrag(targets: () -> List<ReorderTarget>, horizontalOnly: Boolean = false, animateDrop: Boolean = false, verticalOnly: Boolean = false,
    move: (String, String) -> Unit): ReorderDrag {
    val latestTargets by rememberUpdatedState(targets)
    val latestMove by rememberUpdatedState(move)
    return remember(horizontalOnly, animateDrop, verticalOnly) { ReorderDrag({ latestTargets() }, { from, to -> latestMove(from, to) }, horizontalOnly, animateDrop, verticalOnly) }
}

/** Detect on the stable list container so item placement never interrupts the pointer. */
fun Modifier.reorderGestures(drag: ReorderDrag) = onGloballyPositioned { drag.origin = it.positionInRoot() }
    .pointerInput(drag) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val item = drag.hit(down.position) ?: return@awaitEachGesture
            val handle = drag.handles[item.key]?.contains(drag.origin + down.position) == true
            // Long-press cancellation must watch subsequent events, after this down's Main pass.
            if (!handle) awaitPointerEvent(PointerEventPass.Main)
            var initialDelta = Offset.Zero
            val start = if (handle) {
                var activated: PointerInputChange? = null
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed || change.isConsumed) break
                    val delta = change.position - down.position
                    val distance = if (drag.horizontalOnly) kotlin.math.abs(delta.x) else if (drag.verticalOnly) kotlin.math.abs(delta.y) else delta.getDistance()
                    if (distance > viewConfiguration.touchSlop) {
                        initialDelta = delta; change.consume(); activated = down; break
                    }
                }
                activated
            } else awaitLongPressOrCancellation(down.id)
            if (start != null) {
                drag.begin(item, start.position)
                drag.drag(initialDelta)
                try {
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == start.id } ?: break
                        if (!change.pressed) { change.consume(); break }
                        drag.drag(change.positionChange()); change.consume()
                    }
                } finally { drag.finish() }
            }
        }
    }

fun Modifier.reorderTranslation(id: String, drag: ReorderDrag) = zIndex(if (drag.key == id) 1f else 0f)
    .graphicsLayer { val offset = drag.translation(id); translationX = offset.x; translationY = offset.y }

@Composable
fun Modifier.reorderGrip(id: String, drag: ReorderDrag): Modifier {
    val owned = remember(id, drag) { arrayOfNulls<Rect>(1) }
    DisposableEffect(id, drag) { onDispose { if (drag.handles[id] === owned[0]) drag.handles.remove(id) } }
    return onGloballyPositioned { owned[0] = it.boundsInRoot(); drag.handles[id] = owned[0]!! }
}

@Composable
fun ReorderAutoScroll(drag: ReorderDrag, extent: () -> Float, scroll: suspend (Float) -> Float, horizontal: Boolean = false) {
    val edge = with(LocalDensity.current) { 48.dp.toPx() }
    val latestExtent by rememberUpdatedState(extent)
    val latestScroll by rememberUpdatedState(scroll)
    LaunchedEffect(drag.key, drag.pointer) {
        while (drag.key != null && !drag.settling) {
            val position = if (horizontal) drag.pointer.x else drag.pointer.y
            val end = latestExtent()
            if (end <= 0f) break
            val speed = when {
                position < edge -> -(edge - position).coerceAtMost(edge) / 4f
                position > end - edge -> (position - end + edge).coerceAtMost(edge) / 4f
                else -> 0f
            }
            if (speed == 0f) break
            withFrameNanos { }
            if (drag.settling) break
            if (latestScroll(speed) == 0f) break
            drag.checkTarget()
        }
    }
}
