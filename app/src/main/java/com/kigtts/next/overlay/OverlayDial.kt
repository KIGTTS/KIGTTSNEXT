@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.overlay

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.graphics.shapes.Morph
import com.kigtts.next.R
import com.kigtts.next.ui.components.*
import kotlin.math.roundToInt

/** Uses the actual M3E shape catalog, not an approximated star or polygon. */
private class DialShape(private val morph: Morph, private val progress: Float) : Shape {
    private val bounds = morph.calculateMaxBounds()
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

@Composable
internal fun OverlayDial(settings: OverlaySettings, placement: OverlayPlacement, expanded: Boolean, hidden: Boolean,
    toggle: () -> Unit, move: (Float, Float) -> Unit, drop: () -> Unit, interact: () -> Unit,
    open: (OverlayShortcut) -> Unit, configure: () -> Unit, collapsed: () -> Unit = {}) {
    val density = LocalDensity.current
    val expansion = remember { Animatable(0f) }
    val progress = expansion.value
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val onCollapsed by rememberUpdatedState(collapsed)
    val feedback = LocalHapticFeedback.current
    LaunchedEffect(expanded) {
        expansion.animateTo(if (expanded) 1f else 0f, spatial)
        if (!expanded) onCollapsed()
    }
    val effects by animateFloatAsState(if (expanded) 1f else 0f, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "dial-opacity")
    val retraction by animateFloatAsState(if (hidden && !expanded) 1f else 0f, MaterialTheme.motionScheme.slowSpatialSpec(), label = "dial-edge-hide")
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie12Sided) }
    var first by remember { mutableIntStateOf(0) }
    var drag by remember { mutableFloatStateOf(0f) }
    var wheeling by remember { mutableStateOf(false) }
    val wheelOffset by animateFloatAsState(drag, if (wheeling) snap() else MaterialTheme.motionScheme.defaultSpatialSpec(), label = "wheel-settle")
    val stepPx = with(density) { 48.dp.toPx() }
    val cx = with(density) { placement.fabX.toDp() }; val cy = with(density) { placement.fabY.toDp() }
    // Near a cutout or a small freeform window the ring translates to stay within its own safe host.
    val radius = minOf(136f, placement.height / density.density * .35f, placement.width / density.density * .46f, (placement.width / density.density - 176f).coerceAtLeast(48f))
    val ringCenterY = with(density) { (placement.height / 2f).toDp() }
    val scroll = if (expanded) Modifier.pointerInput(settings.shortcuts.size) {
        detectVerticalDragGestures(onDragStart = { interact(); wheeling = true }, onVerticalDrag = { change, amount ->
            change.consume(); drag += amount
            if (kotlin.math.abs(drag) >= stepPx) { first = wrappedIndex(first - (drag / stepPx).toInt(), settings.shortcuts.size); drag %= stepPx }
        }, onDragEnd = { wheeling = false; drag = 0f }, onDragCancel = { wheeling = false; drag = 0f })
    } else Modifier
    // Observe the whole entrance subtree, including gestures starting on a clickable icon.
    Box(Modifier.fillMaxSize().then(scroll)) {
        if (expanded || effects > .001f) {
            val count = settings.shortcuts.size
            val slots = minOf(count, 7)
            repeat(slots) { rawSlot ->
                val slot = if (count > 5) rawSlot - 1 else rawSlot + (5 - slots) / 2
                val item = settings.shortcuts[wrappedIndex(first + slot, count)]
                val angle = (slot - 2) * 36f + wheelOffset / stepPx * 36f
                val radians = Math.toRadians(angle.toDouble())
                val dx = (if (settings.rightEdge) -1 else 1) * kotlin.math.cos(radians).toFloat() * radius
                val dy = kotlin.math.sin(radians).toFloat() * radius
                val edgeAlpha = ((100f - kotlin.math.abs(angle)) / 28f).coerceIn(0f, 1f)
                val x = cx + (dx * progress).dp
                val targetY = ringCenterY + dy.dp
                val y = cy + (targetY - cy) * progress
                Row(Modifier.offset(x = x - 24.dp, y = y - 24.dp)
                    .widthIn(max = 160.dp)
                    .graphicsLayer {
                        translationX = if (settings.rightEdge) -(size.width - 48.dp.toPx()) else 0f
                        transformOrigin = TransformOrigin(if (settings.rightEdge) 1f - 24.dp.toPx() / size.width else 24.dp.toPx() / size.width, .5f)
                        alpha = effects * edgeAlpha; scaleX = .45f + .55f * progress; scaleY = scaleX
                    },
                    verticalAlignment = Alignment.CenterVertically) {
                    @Composable fun Label() {
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface, modifier = Modifier.widthIn(max = 104.dp)) {
                            Text(item.title, Modifier.padding(horizontal = 8.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (item.showLabel && settings.rightEdge) { Label(); Spacer(Modifier.width(8.dp)) }
                    IconActionHint(item.title) {
                        FilledTonalIconButton(onClick = feedbackClick { interact(); open(item) }, enabled = expanded && edgeAlpha > .4f,
                            modifier = Modifier.size(48.dp).iconLongAction(expanded && edgeAlpha > .4f, null).semantics { contentDescription = item.title },
                            shapes = IconButtonDefaults.shapes(shape = CircleShape, pressedShape = MaterialTheme.shapes.medium)) {
                            Icon(MaterialSymbols.vector(item.symbol) ?: Icons.Default.Extension, null, Modifier.size(24.dp))
                        }
                    }
                    if (item.showLabel && !settings.rightEdge) { Spacer(Modifier.width(8.dp)); Label() }
                }
            }
        }
        Box(Modifier.offset(x = cx - 32.dp, y = cy - 32.dp).size(64.dp)
            .graphicsLayer { translationX = (if (settings.rightEdge) 1 else -1) * retraction * 40.dp.toPx(); alpha = 1f - retraction * .48f }
            .pointerInput(Unit) { detectDragGestures(onDragStart = { feedback.performHapticFeedback(HapticFeedbackType.LongPress); interact() }, onDrag = { change, amount -> change.consume(); move(amount.x, amount.y) }, onDragEnd = drop, onDragCancel = drop) }) {
            IconActionHint(if (expanded) "收起快捷入口" else "展开快捷入口", hasLongAction = true) {
                Surface(onClick = feedbackClick(toggle), modifier = Modifier.fillMaxSize()
                    .iconLongAction(true, configure)
                    .semantics { contentDescription = if (expanded) "收起快捷入口" else "展开快捷入口" }
                    .graphicsLayer { rotationZ = progress * 90f },
                    shape = DialShape(morph, progress.coerceIn(0f, 1f)),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Close, null, Modifier.size(28.dp).graphicsLayer { rotationZ = -progress * 90f; alpha = effects })
                        Icon(painterResource(R.drawable.overlay_logo), null, Modifier.size(38.dp).graphicsLayer { rotationZ = -progress * 90f; alpha = 1f - effects })
                    }
                }
            }
        }
    }
}

@Composable internal fun OverlayBubbleContent(notice: OverlayNotice, dismiss: () -> Unit, open: (OverlayShortcut) -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer, shadowElevation = 6.dp, modifier = Modifier.padding(8.dp)) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(notice.text, Modifier.weight(1f).padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = feedbackClick(dismiss), modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Close, "关闭气泡", Modifier.size(18.dp)) }
            }
            notice.shortcut?.let { item ->
                FilledTonalButton(onClick = feedbackClick { open(item) }) {
                    Icon(MaterialSymbols.vector(notice.symbol) ?: Icons.Default.Extension, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(notice.label.orEmpty())
                }
            }
        }
    }
}
