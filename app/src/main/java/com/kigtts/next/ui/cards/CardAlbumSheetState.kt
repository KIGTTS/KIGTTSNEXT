package com.kigtts.next.ui.cards

import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.kigtts.next.ui.components.rememberDragFeedback
import com.kigtts.next.ui.home.rememberDragHandlePress
import kotlinx.coroutines.flow.distinctUntilChanged

/** 0: handle, 1: photographs, 2: albums. Only the handle owns the sheet gesture. */
internal val AlbumSheetSaver = Saver<AnchoredDraggableState<Int>, Int>(
    save = { it.targetValue }, restore = { AnchoredDraggableState(it.coerceIn(0, 2)) })

@Composable internal fun rememberCardAlbumSheet(initial: Int = 0) = rememberSaveable(saver = AlbumSheetSaver) {
    AnchoredDraggableState(initial)
}

internal class CardAlbumDrag(val modifier: Modifier, val active: () -> Boolean)

@Composable internal fun rememberCardAlbumDrag(state: AnchoredDraggableState<Int>, landscape: Boolean, enabled: Boolean): CardAlbumDrag {
    val source = remember { MutableInteractionSource() }
    val feedback = rememberDragFeedback()
    var dragging by remember { mutableStateOf(false) }
    val press = rememberDragHandlePress(feedback, { state.offset.takeIf(Float::isFinite) ?: 0f }, { dragging }, enabled)
    LaunchedEffect(source, feedback) { source.interactions.collect { event -> when (event) {
        is DragInteraction.Start -> { dragging = true; feedback.start(state.offset) }
        is DragInteraction.Stop -> { dragging = false; feedback.finish() }
        is DragInteraction.Cancel -> { dragging = false; feedback.finish(cancelled = true) }
        else -> Unit
    } } }
    LaunchedEffect(state, feedback) {
        snapshotFlow { if (state.offset.isFinite() && state.anchors.size > 0) state.anchors.closestAnchor(state.offset) else null }.distinctUntilChanged().collect {
            if (dragging) feedback.step()
        }
    }
    DisposableEffect(feedback) { onDispose { feedback.finish(cancelled = true) } }
    val modifier = Modifier.then(if (enabled) press.modifier else Modifier).anchoredDraggable(state,
        orientation = if (landscape) Orientation.Horizontal else Orientation.Vertical,
        reverseDirection = true, enabled = enabled, interactionSource = source,
        flingBehavior = AnchoredDraggableDefaults.flingBehavior(state, animationSpec = tween<Float>(240)))
    return CardAlbumDrag(modifier) { enabled && (press.pressed || dragging) }
}
