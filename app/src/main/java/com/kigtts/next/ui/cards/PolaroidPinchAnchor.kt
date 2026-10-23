package com.kigtts.next.ui.cards

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

internal data class PolaroidGridAnchor(val id: String, val viewportY: Float, val paperY: Float, val columns: Int)

internal class PolaroidPinchAnchors {
    var browser: LayoutCoordinates? = null
    val photos = mutableMapOf<Pair<PolaroidBrowseMode, String>, LayoutCoordinates>()
    var pending by mutableStateOf<PolaroidGridAnchor?>(null)

    fun at(point: Offset, mode: PolaroidBrowseMode, focused: String?, columns: Int): PolaroidGridAnchor? {
        val viewport = browser?.takeIf { it.isAttached } ?: return null
        val position = viewport.localToWindow(point)
        val visible = viewport.boundsInWindow()
        val candidates = photos.mapNotNull { (key, value) ->
            if (key.first != mode || !value.isAttached) return@mapNotNull null
            val bounds = value.boundsInWindow()
            if (bounds.width <= 1 || bounds.height <= 1 || !bounds.overlaps(visible)) null else key.second to bounds
        }
        val contained = candidates.filter { it.second.contains(position) }
        val chosen = contained.firstOrNull { mode == PolaroidBrowseMode.STACK && it.first == focused }
            ?: (contained.ifEmpty { candidates }).minByOrNull { (_, bounds) -> (bounds.center - position).getDistanceSquared() }
            ?: return null
        return PolaroidGridAnchor(chosen.first, (point.y / viewport.size.height.coerceAtLeast(1)).coerceIn(0f, 1f),
            ((position.y - chosen.second.top) / chosen.second.height).coerceIn(0f, 1f), columns)
    }
}

internal val LocalPolaroidPinchAnchors = compositionLocalOf<PolaroidPinchAnchors?> { null }

@Composable internal fun Modifier.polaroidPinchTarget(id: String, mode: PolaroidBrowseMode, active: Boolean): Modifier {
    val anchors = LocalPolaroidPinchAnchors.current ?: return this
    val key = mode to id
    val registered = remember { arrayOfNulls<LayoutCoordinates>(1) }
    DisposableEffect(anchors, key, active) {
        onDispose { if (anchors.photos[key] === registered[0]) anchors.photos.remove(key) }
    }
    return if (!active) this else onGloballyPositioned {
        registered[0] = it; anchors.photos[key] = it
    }
}
