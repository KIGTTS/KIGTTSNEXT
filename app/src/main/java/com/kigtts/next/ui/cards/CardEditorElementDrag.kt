package com.kigtts.next.ui.cards

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kigtts.plugin.api.CardLayer
import com.kigtts.plugin.api.CardLayerKind
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

internal class CardEditorElementDrag {
    val sources = mutableMapOf<String, Pair<Rect, CardLayer>>()
    var origin = Offset.Zero
    var paper = Rect.Zero
    var panel = Rect.Zero
    var layer by mutableStateOf<CardLayer?>(null)
    var position by mutableStateOf(Offset.Zero)
    fun placement(): CardLayer? = layer?.takeIf { paper.width > 0f && paper.height > 0f && paper.contains(position) && !panel.contains(position) }
        ?.copy(id = UUID.randomUUID().toString(), x = ((position.x - paper.left) / paper.width).toDouble().coerceIn(0.0, 1.0),
            y = ((position.y - paper.top) / paper.height).toDouble().coerceIn(0.0, 1.0))
}

internal val LocalCardElementDrag = staticCompositionLocalOf<CardEditorElementDrag?> { null }

/** Register a source, while the stable editor root owns its pointer stream across sheet collapse. */
internal fun Modifier.cardElementDragSource(key: String, layer: CardLayer): Modifier = composed {
    val drag = LocalCardElementDrag.current
    if (drag == null) this else {
        DisposableEffect(drag, key) { onDispose { drag.sources.remove(key) } }
        this.onGloballyPositioned { drag.sources[key] = it.boundsInRoot() to layer }
    }
}

internal fun Modifier.cardElementDragRoot(state: CardEditorElementDrag, enabled: Boolean, landscape: Boolean,
    collapse: () -> Unit, insert: (CardLayer) -> Unit): Modifier = composed {
    val available = rememberUpdatedState(enabled)
    val fold = rememberUpdatedState(collapse)
    val drop = rememberUpdatedState(insert)
    val haptics = rememberUpdatedState(LocalHapticFeedback.current)
    this.onGloballyPositioned { state.origin = it.positionInRoot() }.pointerInput(state, landscape) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!available.value) return@awaitEachGesture
            val source = state.sources.values.lastOrNull { it.first.contains(down.position + state.origin) } ?: return@awaitEachGesture
            val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
            state.layer = source.second; state.position = held.position + state.origin
            haptics.value.performHapticFeedback(HapticFeedbackType.LongPress)
            held.consume()
            var leftPanel = false
            var cancelled = false
            try {
                while (true) {
                    // Consume before the grid/outer scroll view. Neither owns the gesture after pickup.
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (!available.value || event.changes.count { it.pressed } > 1) cancelled = true
                    val change = event.changes.firstOrNull { it.id == held.id } ?: break
                    state.position = change.position + state.origin
                    event.changes.forEach { it.consume() }
                    if (!leftPanel && !state.panel.contains(state.position)) { leftPanel = true; fold.value() }
                    if (!change.pressed) {
                        if (!cancelled) state.placement()?.let { drop.value(it); haptics.value.performHapticFeedback(HapticFeedbackType.GestureEnd) }
                        break
                    }
                }
            } finally { state.layer = null }
        }
    }
}

@Composable internal fun CardEditorElementDragOverlay(state: CardEditorElementDrag, imageFile: (String) -> File?) {
    val layer = state.layer ?: return
    Surface(Modifier.offset { IntOffset((state.position.x - state.origin.x).roundToInt(), (state.position.y - state.origin.y).roundToInt()) }
        .offset((-44).dp, (-44).dp).size(88.dp).testTag("card-element-drag-preview"),
        shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .85f), shadowElevation = 12.dp) {
        Box(Modifier.padding(6.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
            if (layer.kind == CardLayerKind.TEXT) Text(layer.text, color = MaterialTheme.colorScheme.onSurface)
            else {
                val image = layer.imageId?.let { rememberCardImage(imageFile(it), 256) }
                if (image != null) Image(image, null, Modifier.fillMaxSize()) else Icon(Icons.Default.Image, null)
            }
        }
    }
}
