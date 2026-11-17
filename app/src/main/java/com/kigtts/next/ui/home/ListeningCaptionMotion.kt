@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.kigtts.next.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp

internal data class CaptionVisuals(val finalOnly: Boolean = false, val reducedMotion: Boolean = false, val reflow: Boolean = true)

internal class ListeningCaptionMotion(val modifier: Modifier, val onLayout: (TextLayoutResult) -> Unit)

/** Animate the first speech and changes to line geometry, not incremental word updates. */
@Composable
internal fun rememberListeningCaptionMotion(standby: Boolean, visuals: CaptionVisuals, viewport: Int): ListeningCaptionMotion {
    val lift = with(LocalDensity.current) { 8.dp.toPx() }
    val scheme = MaterialTheme.motionScheme
    val entrance = remember { Animatable(1f) }
    val reflow = remember { Animatable(0f) }
    var geometry by remember { mutableStateOf<CaptionGeometry?>(null) }
    val history = remember { CaptionGeometryHistory() }
    LaunchedEffect(standby, visuals.reducedMotion) {
        if (standby || visuals.reducedMotion) entrance.snapTo(1f)
        else {
            entrance.snapTo(0f)
            entrance.animateTo(1f, scheme.fastSpatialSpec())
        }
    }
    LaunchedEffect(standby, geometry, visuals.reducedMotion, visuals.reflow) {
        val before = history.previous
        val now = geometry?.takeUnless { standby }
        history.previous = now
        if (!visuals.reducedMotion && visuals.reflow && before != null && now != null &&
            before.lines != now.lines && viewport >= maxOf(before.height, now.height)) {
            val displacement = ((now.height - before.height) * .5f).coerceIn(-2 * lift, 2 * lift)
            reflow.snapTo(reflow.value + displacement)
            reflow.animateTo(0f, scheme.fastSpatialSpec())
        } else if (standby || visuals.reducedMotion || !visuals.reflow) reflow.snapTo(0f)
        else if (reflow.value != 0f) reflow.animateTo(0f, scheme.fastSpatialSpec())
    }
    return ListeningCaptionMotion(Modifier.graphicsLayer {
        val shown = entrance.value.coerceIn(0f, 1f)
        alpha = if (visuals.finalOnly || standby || visuals.reducedMotion) 1f else .45f + .55f * shown
        translationY = if (visuals.reducedMotion) 0f else lift * (1f - shown) + reflow.value
    }) { layout -> geometry = CaptionGeometry(layout.lineCount, layout.size.height) }
}

private data class CaptionGeometry(val lines: Int, val height: Int)
private class CaptionGeometryHistory(var previous: CaptionGeometry? = null)
