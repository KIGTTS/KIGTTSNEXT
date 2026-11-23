package com.kigtts.next.overlay

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp

@Stable
internal class OverlayDialMotion(private val point: State<Offset>, private val fraction: State<Float>) {
    val center get() = point.value
    val progress get() = fraction.value
}

/** One timeline moves the visible anchor and changes its shape, including interrupted transitions. */
@Composable
internal fun rememberOverlayDialMotion(placement: OverlayPlacement, expanded: Boolean,
    initialCenter: Offset?, collapsedCenter: Offset?, compactLayout: (() -> OverlayPlacement)?, collapsed: () -> Unit): OverlayDialMotion {
    val progress = remember { mutableFloatStateOf(0f) }
    val center = remember(placement.x, placement.y, placement.width, placement.height, initialCenter) {
        mutableStateOf(initialCenter ?: Offset(placement.fabX, placement.fabY))
    }
    val target = if (expanded) Offset(placement.fabX, placement.fabY)
        else collapsedCenter ?: Offset(placement.fabX, placement.fabY)
    val clock = remember { Animatable(0f) }
    val onCollapsed by rememberUpdatedState(collapsed)
    val compact = compactLayout != null
    val latestCompactLayout by rememberUpdatedState(compactLayout)
    LaunchedEffect(expanded, compact, center, target) {
        if (compact) {
            progress.floatValue = 0f
            return@LaunchedEffect
        }
        val fromProgress = progress.floatValue
        val fromCenter = center.value
        val endProgress = if (expanded) 1f else 0f
        if (fromProgress != endProgress || fromCenter != target) {
            clock.snapTo(0f)
            clock.animateTo(1f, tween(280, easing = FastOutSlowInEasing)) {
                progress.floatValue = fromProgress + (endProgress - fromProgress) * value
                center.value = lerp(fromCenter, target, value)
            }
        }
        if (!expanded) onCollapsed()
    }
    return remember(center) {
        val actualCenter = derivedStateOf { latestCompactLayout?.invoke()?.let { Offset(it.fabX, it.fabY) } ?: center.value }
        OverlayDialMotion(actualCenter, progress)
    }
}
