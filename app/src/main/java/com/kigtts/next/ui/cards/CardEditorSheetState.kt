@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.kigtts.next.ui.cards

import androidx.compose.foundation.gestures.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

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
