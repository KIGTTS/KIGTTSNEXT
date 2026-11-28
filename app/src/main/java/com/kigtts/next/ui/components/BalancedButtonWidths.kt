@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.kigtts.next.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** Each press borrows the same number of dp, regardless of label length. */
internal fun balancedButtonWidths(base: List<Float>, presses: List<Float>, minimum: Float, expansion: Float): List<Float> {
    if (expansion <= 0f || presses.none { it > 0f }) return base
    val widths = base.toMutableList()
    base.indices.forEach { index ->
        if (base[index] <= 0f || presses[index] <= 0f) return@forEach
        // Stable extension placeholders have zero width and are not visual neighbours.
        val neighbors = listOfNotNull((index - 1 downTo 0).firstOrNull { base[it] > 0f },
            (index + 1..base.lastIndex).firstOrNull { base[it] > 0f })
        var remaining = expansion * presses[index].coerceIn(0f, 1f)
        // A second pass transfers any share that a small neighbor could not supply.
        repeat(2) {
            val available = neighbors.filter { widths[it] > minimum }
            if (available.isNotEmpty()) {
                val share = remaining / available.size
                available.forEach { neighbor ->
                    val borrowed = share.coerceAtMost(widths[neighbor] - minimum)
                    widths[neighbor] -= borrowed
                    widths[index] += borrowed
                    remaining -= borrowed
                }
            }
        }
    }
    return widths
}

@Composable
internal fun rememberBalancedWidths(
    base: List<Float>, sources: List<MutableInteractionSource>, minimum: Float = 24f, expansion: Float = 6f,
): List<Float> = rememberBalancedWidthProvider(base, sources, minimum, expansion).invoke()

@Composable
internal fun rememberBalancedWidthProvider(
    base: List<Float>, sources: List<MutableInteractionSource>, minimum: Float = 24f, expansion: Float = 6f,
): () -> List<Float> {
    val presses = sources.map { source ->
        animateFloatAsState(if (source.collectIsPressedAsState().value) 1f else 0f,
            MaterialTheme.motionScheme.fastSpatialSpec(), label = "button-width-press")
    }
    return { balancedButtonWidths(base, presses.map { it.value }, minimum, expansion) }
}
