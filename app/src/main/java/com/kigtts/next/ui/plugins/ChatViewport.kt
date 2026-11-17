package com.kigtts.next.ui.plugins

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Constraints

private class ChatViewportMeasure { var width = 0; var height = 0; var historyHeight = 0 }

/** IME frames move a cached history layer, rather than reflowing every Markdown table and paragraph. */
@Composable internal fun ChatViewport(modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {}, followIme: Boolean = false,
    keyboardInsets: WindowInsets = WindowInsets.ime,
    history: @Composable () -> Unit, composer: @Composable () -> Unit) {
    val config = LocalConfiguration.current
    val measure = remember(config.orientation, config.screenWidthDp) { ChatViewportMeasure() }
    var consumed by remember { mutableStateOf(WindowInsets(0, 0, 0, 0)) }
    Layout(content = {
        // Clip to the visible history viewport, even while its cached child remains taller during IME animation.
        Layout(content = history, modifier = Modifier.clipToBounds()) { children, viewport ->
            val content = children.map { it.measure(Constraints.fixed(viewport.maxWidth, measure.historyHeight)) }
            layout(viewport.maxWidth, viewport.maxHeight) {
                // Keep one bottom anchor while the IME moves. Toggling auto-follow must never
                // change this transform, and the layer retains Markdown drawing between frames.
                content.forEach { it.placeRelativeWithLayer(0, viewport.maxHeight - measure.historyHeight) }
            }
        }
        Box { composer() }
        Box { overlay() }
    }, modifier = modifier.clipToBounds().then(if (followIme) Modifier.onConsumedWindowInsetsChanged { consumed = it } else Modifier)) { items, constraints ->
        val width = constraints.maxWidth; val height = constraints.maxHeight
        if (measure.width != width) { measure.width = width; measure.height = height }
        measure.height = maxOf(measure.height, height)
        // Read the live IME offset in layout, after subtracting the host's safe bottom padding.
        // Measuring the composer with the full height keeps its field/row stable across IME frames.
        val keyboard = if (followIme) (keyboardInsets.getBottom(this) - consumed.getBottom(this)).coerceIn(0, height) else 0
        val input = items[1].measure(Constraints(maxWidth = width, maxHeight = height, minWidth = width))
        val visibleHeight = (height - input.height - keyboard).coerceAtLeast(0)
        measure.historyHeight = (measure.height - input.height).coerceAtLeast(0)
        val content = items[0].measure(Constraints.fixed(width, visibleHeight))
        val floating = items[2].measure(Constraints.fixed(width, visibleHeight))
        layout(width, height) {
            content.placeRelative(0, 0)
            floating.placeRelative(0, 0)
            input.placeRelative(0, visibleHeight)
        }
    }
}
