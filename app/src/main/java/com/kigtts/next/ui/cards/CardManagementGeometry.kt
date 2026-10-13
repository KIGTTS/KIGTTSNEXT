package com.kigtts.next.ui.cards

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

internal data class ManagementPaperGeometry(val source: Rect, val target: Rect, val browser: Rect)
internal class CardManagementGeometry {
    val sources = mutableMapOf<String, LayoutCoordinates>()
    var targetId: String? = null
    var target: LayoutCoordinates? = null
    var browser: LayoutCoordinates? = null
    private var pressedSource: Rect? = null
    var geometry by mutableStateOf<ManagementPaperGeometry?>(null)
    fun begin(id: String) { pressedSource = sources[id]?.takeIf { it.isAttached }?.boundsInWindow() }
    fun capture() {
        if (geometry != null) return
        val source = pressedSource ?: sources[targetId]?.takeIf { it.isAttached }?.boundsInWindow() ?: return
        val destination = target?.takeIf { it.isAttached }?.boundsInWindow() ?: return
        val viewport = browser?.takeIf { it.isAttached }?.boundsInWindow() ?: return
        if (source.width > 0f && destination.width > 0f) geometry = ManagementPaperGeometry(source, destination, viewport)
    }
    fun reset() { geometry = null; pressedSource = null }
}
internal val LocalCardManagementGeometry = compositionLocalOf<CardManagementGeometry?> { null }
internal val LocalCardManagementReveal = compositionLocalOf<State<Float>> { mutableStateOf(0f) }
internal val LocalCardManagementActive = compositionLocalOf { false }

/** Registers the actually painted large paper, including the existing resize-layer compensation. */
@Composable
internal fun Modifier.cardManagementSource(id: String, active: Boolean = true): Modifier {
    val geometry = LocalCardManagementGeometry.current ?: return this
    var coordinates by remember(geometry, id) { mutableStateOf<LayoutCoordinates?>(null) }
    DisposableEffect(geometry, id, active) {
        onDispose { if (geometry.sources[id] === coordinates) geometry.sources.remove(id) }
    }
    return if (!active) this else onGloballyPositioned {
        coordinates = it
        geometry.sources[id] = it
    }
}

internal fun managementPaperCenter(geometry: ManagementPaperGeometry, progress: Float): Offset =
    geometry.source.center + (geometry.target.center - geometry.source.center) * progress
