package com.kigtts.next.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/** Regions that own horizontal gestures. Registration never consumes their pointer events. */
internal class HomeSwipeExclusions {
    private val regions = mutableMapOf<Any, Rect>()
    fun update(owner: Any, bounds: Rect) { regions[owner] = bounds }
    fun remove(owner: Any) { regions.remove(owner) }
    fun contains(point: Offset) = regions.values.any { it.contains(point) }
}

internal val LocalHomeSwipeExclusions = staticCompositionLocalOf<HomeSwipeExclusions?> { null }

/** Scoped to this surface's lifetime; web content and plugin surfaces share the same contract. */
fun Modifier.excludeHomeSideSwipes(enabled: Boolean = true): Modifier = composed {
    val exclusions = LocalHomeSwipeExclusions.current
    val owner = remember { Any() }
    DisposableEffect(exclusions, enabled) { onDispose { exclusions?.remove(owner) } }
    if (!enabled || exclusions == null) this else onGloballyPositioned { exclusions.update(owner, it.boundsInRoot()) }
}
