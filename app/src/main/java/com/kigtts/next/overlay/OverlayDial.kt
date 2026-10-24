@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.overlay

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalView
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.BitmapFactory
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
    open: (OverlayShortcut) -> Unit, configure: () -> Unit, collapsed: () -> Unit = {}, reorder: (String, Int) -> Boolean = { _, _ -> false }) {
    val density = LocalDensity.current
    val hostView = LocalView.current
    val hostLocation = remember { IntArray(2) }
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
    var wheel by remember { mutableStateOf(OverlayWheelPosition()) }
    var sorting by remember { mutableStateOf<String?>(null) }
    var sortCenter by remember { mutableStateOf<Offset?>(null) }
    val reorderMotion by animateFloatAsState(if (sorting != null) 1f else 0f,
        MaterialTheme.motionScheme.defaultSpatialSpec(), label = "reorder-settle")
    var wheeling by remember { mutableStateOf(false) }
    val stepPx = with(density) { 48.dp.toPx() }
    val stretch = overlayWheelStretch(wheel, settings.shortcuts.size, stepPx)
    val animatedWheelOffset by animateFloatAsState(stretch, if (wheeling) snap() else
        spring(dampingRatio = .78f, stiffness = 400f), label = "wheel-settle")
    val wheelOffset = if (wheeling) stretch else animatedWheelOffset
    LaunchedEffect(settings.shortcuts.size) {
        wheel = wheel.copy(first = wheel.first.coerceIn(0, (settings.shortcuts.size - 5).coerceAtLeast(0)))
    }
    val cx = with(density) { placement.fabX.toDp() }; val cy = with(density) { placement.fabY.toDp() }
    // Near a cutout or a small freeform window the ring translates to stay within its own safe host.
    val radius = minOf(136f, placement.height / density.density * .35f, placement.width / density.density * .46f, (placement.width / density.density - 176f).coerceAtLeast(48f))
    val ringCenterY = with(density) { (placement.height / 2f).toDp() }
    val scroll = if (expanded) Modifier.pointerInput(settings.shortcuts.size) {
        detectVerticalDragGestures(onDragStart = {
            interact(); wheel = wheel.copy(drag = animatedWheelOffset); wheeling = true
        }, onVerticalDrag = { change, amount ->
            change.consume(); wheel = dragOverlayWheel(wheel, amount, settings.shortcuts.size, stepPx)
        }, onDragEnd = { wheeling = false; wheel = settleOverlayWheel(wheel, settings.shortcuts.size, stepPx) },
            onDragCancel = { wheeling = false; wheel = wheel.copy(drag = 0f) })
    } else Modifier
    // Observe the whole entrance subtree, including gestures starting on a clickable icon.
    // WindowManager moves the surface independently of Compose recomposition. Keep the
    // rendered absolute anchor stable even when one frame still has the previous placement.
    Box(Modifier.fillMaxSize().drawWithContent {
        val resizing = hostView.width != placement.width || hostView.height != placement.height
        hostView.getLocationOnScreen(hostLocation)
        translate(left = if (resizing) (placement.x - hostLocation[0]).toFloat() else 0f, top = if (resizing) (placement.y - hostLocation[1]).toFloat() else 0f) {
            this@drawWithContent.drawContent()
        }
    }.then(scroll)) {
        if (expanded || effects > .001f) {
            val count = settings.shortcuts.size
            settings.shortcuts.forEachIndexed { index, item ->
                val slot = index - wheel.first + (5 - count).coerceAtLeast(0) / 2f
                val angle = (slot - 2) * 36f + wheelOffset / stepPx * 36f
                if (kotlin.math.abs(angle) > 136f && sorting != item.id) return@forEachIndexed
                key(item.id) {
                val held = sorting == item.id
                val radians = Math.toRadians(angle.toDouble())
                val dx = (if (settings.rightEdge) -1 else 1) * kotlin.math.cos(radians).toFloat() * radius
                val dy = kotlin.math.sin(radians).toFloat() * radius
                val edgeAlpha = ((100f - kotlin.math.abs(angle)) / 28f).coerceIn(0f, 1f)
                val x = cx + (dx * progress).dp
                val targetY = ringCenterY + dy.dp
                val y = cy + (targetY - cy) * progress
                val settledX by animateDpAsState(x, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "entrance-x")
                val settledY by animateDpAsState(y, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "entrance-y")
                val settling = sorting != null || reorderMotion > .001f
                val displayedX = if (!settling || wheeling) x else settledX
                val displayedY = if (!settling || wheeling) y else settledY
                val pointerDelta = if (held) sortCenter?.let {
                    it - Offset(with(density) { displayedX.toPx() }, with(density) { displayedY.toPx() })
                } ?: Offset.Zero else Offset.Zero
                val releasedDelta by animateOffsetAsState(pointerDelta, if (held) snap() else MaterialTheme.motionScheme.defaultSpatialSpec(), label = "entrance-drop")
                val followDelta = if (held) pointerDelta else releasedDelta
                val labelLift = (kotlin.math.sign(angle) * ((kotlin.math.abs(angle) - 36f) / 36f).coerceIn(0f, 1f) * 8f * progress).dp
                Row(Modifier.offset(x = displayedX - 24.dp, y = displayedY - 24.dp)
                    .widthIn(max = 160.dp)
                    .graphicsLayer {
                        translationX = if (settings.rightEdge) -(size.width - 48.dp.toPx()) else 0f
                        translationX += followDelta.x
                        translationY = followDelta.y
                        transformOrigin = TransformOrigin(if (settings.rightEdge) 1f - 24.dp.toPx() / size.width else 24.dp.toPx() / size.width, .5f)
                        alpha = effects * if (held) 1f else edgeAlpha * if (item.enabled) 1f else .45f; scaleX = .45f + .55f * progress; scaleY = scaleX
                    },
                    verticalAlignment = Alignment.CenterVertically) {
                    @Composable fun Label() {
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface, modifier = Modifier.offset(y = labelLift).widthIn(max = 104.dp)) {
                            Text(item.title, Modifier.padding(horizontal = 8.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if ((settings.showLabels && item.showLabel || !settings.showLabels && sorting == item.id) && settings.rightEdge) { Label(); Spacer(Modifier.width(8.dp)) }
                    IconActionHint(item.title, hasLongAction = true) {
                        FilledTonalIconButton(onClick = feedbackClick { if (item.enabled) { interact(); open(item) } }, enabled = expanded && edgeAlpha > .4f && item.enabled,
                            modifier = Modifier.size(48.dp).overlayEntranceReorder(item.id, expanded && (held || edgeAlpha > .4f),
                                { sorting = it; interact() }, reorder,
                                { wheel = wheel.copy(first = (wheel.first - 1).coerceAtLeast(0)) }, { sortCenter = it })
                                .semantics { contentDescription = item.title; if (edgeAlpha <= .4f && !held) hideFromAccessibility() },
                            shapes = IconButtonDefaults.shapes(shape = CircleShape, pressedShape = MaterialTheme.shapes.medium)) {
                            val appIcon by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, item.iconPath) {
                                value = withContext(Dispatchers.IO) { item.iconPath?.let { path -> runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull() } }
                            }
                            Box {
                                if (appIcon != null) Image(appIcon!!, item.title, Modifier.size(24.dp))
                                else Icon(MaterialSymbols.vector(item.symbol) ?: Icons.Default.Extension, null, Modifier.size(24.dp))
                                item.badge?.takeIf { it.count > 0 || it.kind == "dot" }?.let { badge ->
                                    val label = if (badge.kind == "number") badge.count.coerceIn(0, 99).let { if (badge.count > 99) "99+" else it.toString() } else null
                                    Surface(shape = CircleShape, color = if (badge.tone == "warning") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.align(Alignment.TopEnd).size(if (label == null) 8.dp else 18.dp)) {
                                        if (label != null) Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 2.dp), maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                    if ((settings.showLabels && item.showLabel || !settings.showLabels && sorting == item.id) && !settings.rightEdge) { Spacer(Modifier.width(8.dp)); Label() }
                }
                }
            }
        }
        val fabSize = (56f + 8f * progress.coerceIn(0f, 1f)).dp
        Box(Modifier.offset(x = cx - fabSize / 2, y = cy - fabSize / 2).size(fabSize)
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
                        Icon(painterResource(R.drawable.overlay_logo), null, Modifier.size(28.dp).graphicsLayer { rotationZ = -progress * 90f; alpha = 1f - effects })
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
