package com.kigtts.next.ui.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth

private class PaperMeasure { var endpoint: Constraints? = null }

/** Keep the photo, stickers, caption and paper in one coordinate system while bounds morph. */
@Composable internal fun Modifier.scalePolaroidToLookahead(): Modifier {
    val measure = remember { PaperMeasure() }
    return layout { child, constraints ->
        if (isLookingAhead) measure.endpoint = constraints
        val placed = child.measure(measure.endpoint ?: constraints)
        val width = constraints.constrainWidth(placed.width)
        val height = constraints.constrainHeight(placed.height)
        layout(width, height) { placed.placeWithLayer(0, 0) {
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = width.toFloat() / placed.width.coerceAtLeast(1)
            scaleY = height.toFloat() / placed.height.coerceAtLeast(1)
            clip = false
        } }
    }
}
