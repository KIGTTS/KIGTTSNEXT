@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.kigtts.next.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalCornerRadius = compositionLocalOf { 24.dp }

data class CornerNeighbors(
    val start: Boolean = false, val end: Boolean = false,
    val above: Boolean = false, val below: Boolean = false,
)

/** Home cards share a 4–20 dp range while keeping connected-edge motion. */
@Composable
fun homeCardShape(
    neighbors: CornerNeighbors = CornerNeighbors(),
    pressed: Boolean = false, neighborPressed: Boolean = false,
): RoundedCornerShape = connectedShape(neighbors, pressed, neighborPressed,
    outer = LocalCornerRadius.current.coerceIn(4.dp, 20.dp))

/** Close edges share small corners; exposed edges retain the user's radius. */
@Composable
fun connectedShape(
    neighbors: CornerNeighbors = CornerNeighbors(),
    pressed: Boolean = false, neighborPressed: Boolean = false,
    outer: Dp = LocalCornerRadius.current,
): RoundedCornerShape {
    val radius = outer.coerceIn(4.dp, 24.dp)
    val inner = (LocalCornerRadius.current * .22f).coerceIn(4.dp, radius)
    @Composable fun corner(connected: Boolean, label: String): Dp {
        val target = when {
            pressed -> if (connected) 4.dp else radius * .45f
            neighborPressed -> if (connected) inner * .55f else radius * .8f
            connected -> inner
            else -> radius
        }
        return animateDpAsState(target.coerceIn(4.dp, radius), MaterialTheme.motionScheme.fastSpatialSpec(), label = label)
            .value.coerceIn(4.dp, 24.dp)
    }
    return RoundedCornerShape(
        topStart = corner(neighbors.start || neighbors.above, "top-start"),
        topEnd = corner(neighbors.end || neighbors.above, "top-end"),
        bottomEnd = corner(neighbors.end || neighbors.below, "bottom-end"),
        bottomStart = corner(neighbors.start || neighbors.below, "bottom-start"),
    )
}
