@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package com.kigtts.next.ui.cards

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex

/** Draw each small paper from the selected large-paper position out to its real horizontal slot. */
@Composable
internal fun Modifier.cardManagementPaper(index: Int, selectedIndex: Int, reveal: State<Float>,
    paperWidth: Dp, spacing: Dp, cardId: String): Modifier {
    // Shared papers already travel to their own final slot; applying the old whole-stack
    // transform again would shrink the destination and make the animation fight itself.
    if (LocalPolaroidSharedScope.current != null) return this
    val host = LocalCardManagementGeometry.current
    var slot by remember(host, cardId) { mutableStateOf<LayoutCoordinates?>(null) }
    var bounds by remember(host, cardId) { mutableStateOf(Rect.Zero) }
    DisposableEffect(host, cardId) { onDispose { if (host != null && host.target === slot) { host.target = null; host.targetId = null } } }
    return onGloballyPositioned {
        slot = it
        if (host?.geometry == null || reveal.value >= .999f) bounds = it.boundsInWindow()
        if (index == selectedIndex) { host?.targetId = cardId; host?.target = it }
    }.zIndex(if (index == selectedIndex) 1000f else 999f - kotlin.math.abs(index - selectedIndex))
        .graphicsLayer {
            scaleX = 1f; scaleY = 1f; translationX = 0f; translationY = 0f
            val phase = reveal.value.coerceIn(0f, 1f)
            val geometry = host?.geometry
            val target = bounds.takeIf { it.width > 0f } ?: geometry?.target?.translate(
                Offset((index - selectedIndex) * (paperWidth + spacing).toPx(), 0f))
            if (geometry != null && target != null) {
                scaleX = geometry.source.width / target.width.coerceAtLeast(1f) * (1f - phase) + phase
                scaleY = scaleX
                val center = geometry.source.center + (target.center - geometry.source.center) * phase
                val delta = center - target.center
                translationX = delta.x; translationY = delta.y
            }
            // The selected paper overlaps the shrinking browser exactly; neighbors emerge behind it.
            alpha = if (index == selectedIndex) 1f else phase
        }
}
