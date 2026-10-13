package com.kigtts.next.ui.cards

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints

/** Both child slots survive rotation. Only endpoint constraints and placement change. */
@Composable internal fun CardBrowsingLayout(landscape: Boolean, browser: @Composable () -> Unit,
    albums: @Composable () -> Unit) {
    Layout(content = { Box(Modifier.fillMaxSize()) { browser() }; albums() }, modifier = Modifier.fillMaxSize()) { children, constraints ->
        val width = constraints.maxWidth; val height = constraints.maxHeight
        val dock = children[1].measure(Constraints(maxWidth = width, maxHeight = height))
        val bodyWidth = if (landscape) (width - dock.width).coerceAtLeast(0) else width
        val bodyHeight = if (landscape) height else (height - dock.height).coerceAtLeast(0)
        val body = children[0].measure(Constraints.fixed(bodyWidth, bodyHeight))
        layout(width, height) {
            body.placeRelative(0, 0)
            dock.placeRelative(if (landscape) width - dock.width else 0, if (landscape) 0 else height - dock.height)
        }
    }
}
