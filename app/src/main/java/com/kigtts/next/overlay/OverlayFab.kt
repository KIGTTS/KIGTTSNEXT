@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.overlay

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import androidx.graphics.shapes.Morph
import com.kigtts.next.R
import com.kigtts.next.ui.components.*

/** Uses the actual M3E shape catalog, not an approximated star or polygon. */
private class DialShape(private val morph: Morph, private val bounds: FloatArray, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        fun x(value: Float) = (value - bounds[0]) / (bounds[2] - bounds[0]) * size.width
        fun y(value: Float) = (value - bounds[1]) / (bounds[3] - bounds[1]) * size.height
        morph.asCubics(progress).forEachIndexed { index, cubic ->
            if (index == 0) path.moveTo(x(cubic.anchor0X), y(cubic.anchor0Y))
            path.cubicTo(x(cubic.control0X), y(cubic.control0Y), x(cubic.control1X), y(cubic.control1Y), x(cubic.anchor1X), y(cubic.anchor1Y))
        }
        path.close(); return Outline.Generic(path)
    }
}

/** Wheel scrolling does not rebuild the morph, icon painters or FAB touch handlers. */
@Composable
internal fun OverlayFab(motion: OverlayDialMotion, expanded: Boolean, rightEdge: Boolean,
    retraction: Float, nativeRetraction: Boolean, effects: State<Float>,
    toggle: () -> Unit, move: (Float, Float) -> Unit, drop: () -> Unit, interact: () -> Unit, configure: () -> Unit) {
    val density = LocalDensity.current
    val progress = motion.progress
    val cx = with(density) { motion.center.x.toDp() }; val cy = with(density) { motion.center.y.toDp() }
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie12Sided) }
    val morphBounds = remember(morph) { morph.calculateMaxBounds() }
    val shape = remember(morph, progress) { DialShape(morph, morphBounds, progress.coerceIn(0f, 1f)) }
    val fabSize = (56f + 8f * progress.coerceIn(0f, 1f)).dp
    // The native touch window becomes half-width at the edge. Keep a full-size square
    // child and clip it at that window; measuring against the narrow host makes an oval.
    Box(Modifier.zIndex(3f).offset(x = cx - fabSize / 2, y = cy - fabSize / 2)
        .wrapContentSize(Alignment.TopStart, unbounded = true).size(fabSize)
        .graphicsLayer {
            translationX = if (!nativeRetraction) (if (rightEdge) 1 else -1) * retraction * 40.dp.toPx() else 0f
            alpha = 1f - retraction * .48f
        }
        .overlayFabDrag(move, drop, interact, enabled = !expanded)) {
        val label = if (expanded) uiText("overlay.dial.collapse") else uiText("overlay.dial.expand")
        IconActionHint(label, hasLongAction = true) {
            Surface(onClick = feedbackClick(toggle), modifier = Modifier.fillMaxSize()
                .iconLongAction(true, configure)
                .semantics { contentDescription = label }
                .graphicsLayer { rotationZ = progress * 90f },
                shape = shape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, null, Modifier.size(28.dp).graphicsLayer { rotationZ = -progress * 90f; alpha = effects.value })
                    Icon(painterResource(R.drawable.overlay_logo), null, Modifier.size(28.dp).graphicsLayer { rotationZ = -progress * 90f; alpha = 1f - effects.value })
                }
            }
        }
    }
}
