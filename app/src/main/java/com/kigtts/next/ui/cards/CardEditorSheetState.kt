@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.kigtts.next.ui.cards

import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.kigtts.next.ui.components.rememberDragFeedback

internal enum class CardToolsPosition { EXPANDED, COLLAPSED }
private val toolsStateSaver = Saver<AnchoredDraggableState<CardToolsPosition>, String>(
    save = { it.settledValue.name }, restore = { AnchoredDraggableState(CardToolsPosition.valueOf(it)) })

/** Only the header owns the sheet's native drag node. Tool content owns its own scrolling. */
@Composable internal fun rememberCardToolsSheet(expandedTop: Float, foldedTop: Float): AnchoredDraggableState<CardToolsPosition> {
    val state = rememberSaveable(saver = toolsStateSaver) { AnchoredDraggableState(CardToolsPosition.EXPANDED) }
    SideEffect { state.updateAnchors(DraggableAnchors {
        CardToolsPosition.EXPANDED at expandedTop
        CardToolsPosition.COLLAPSED at foldedTop
    }, state.targetValue) }
    return state
}

@Composable internal fun Modifier.cardToolsDrag(state: AnchoredDraggableState<CardToolsPosition>, enabled: Boolean): Modifier {
    val source = remember { MutableInteractionSource() }
    val feedback = rememberDragFeedback()
    LaunchedEffect(source, feedback) { source.interactions.collect { event -> when (event) {
        is DragInteraction.Start -> feedback.start(state.offset.takeIf(Float::isFinite) ?: 0f)
        is DragInteraction.Stop -> feedback.finish()
        is DragInteraction.Cancel -> feedback.finish(cancelled = true)
        else -> Unit
    } } }
    return anchoredDraggable(state, Orientation.Vertical, enabled = enabled, interactionSource = source,
        flingBehavior = AnchoredDraggableDefaults.flingBehavior(state, animationSpec = tween<Float>(220)))
}
