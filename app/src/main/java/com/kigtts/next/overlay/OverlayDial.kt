@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.overlay

import com.kigtts.next.i18n.uiText

import androidx.compose.animation.core.*
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import com.kigtts.next.ui.components.*
import kotlin.math.roundToInt

@Composable
internal fun OverlayDial(settings: OverlaySettings, placement: OverlayPlacement, expanded: Boolean, hidden: Boolean,
    toggle: () -> Unit, move: (Float, Float) -> Unit, drop: () -> Unit, interact: () -> Unit,
    open: (OverlayShortcut) -> Unit, configure: () -> Unit, collapsed: () -> Unit = {}, reorder: (String, Int) -> Boolean = { _, _ -> false },
    edgeRetraction: (() -> Float)? = null, initialCenter: Offset? = null, collapsedCenter: Offset? = null,
    compactLayout: (() -> OverlayPlacement)? = null, centerChanged: (Offset) -> Unit = {}) {
    val density = LocalDensity.current
    val hostView = LocalView.current
    val hostLocation = remember { IntArray(2) }
    val motion = rememberOverlayDialMotion(placement, expanded, initialCenter, collapsedCenter, compactLayout, collapsed)
    val onCenter by rememberUpdatedState(centerChanged)
    LaunchedEffect(motion) { snapshotFlow { motion.center }.collect { onCenter(it) } }
    val effects = animateFloatAsState(if (expanded) 1f else 0f, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "dial-opacity")
    val retraction = edgeRetraction ?: run {
        val animated = animateFloatAsState(if (hidden && !expanded) 1f else 0f,
            tween(220, easing = FastOutSlowInEasing), label = "dial-edge-hide")
        val progress: () -> Float = { animated.value }
        progress
    }
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
    val currentWheelOffset by rememberUpdatedState(wheelOffset)
    LaunchedEffect(settings.shortcuts.size) {
        wheel = wheel.copy(first = wheel.first.coerceIn(0, (settings.shortcuts.size - 5).coerceAtLeast(0)))
    }
    val radius = minOf(136f, placement.height / density.density * .35f, placement.width / density.density * .46f, (placement.width / density.density - 176f).coerceAtLeast(48f))
    val isExpanded by rememberUpdatedState(expanded)
    // The expanded dial keeps its original vertical scrolling and endpoint resistance.
    val scroll = Modifier.pointerInput(settings.shortcuts.size) {
        detectVerticalDragGestures(onDragStart = {
            if (isExpanded) { interact(); wheel = wheel.copy(drag = currentWheelOffset); wheeling = true }
        }, onVerticalDrag = { change, amount ->
            if (isExpanded) { change.consume(); wheel = dragOverlayWheel(wheel, amount, settings.shortcuts.size, stepPx) }
        }, onDragEnd = { wheeling = false; wheel = settleOverlayWheel(wheel, settings.shortcuts.size, stepPx) },
            onDragCancel = { wheeling = false; wheel = wheel.copy(drag = 0f) })
    }
    // Observe the whole entrance subtree, including gestures starting on a clickable icon.
    // WindowManager moves the surface independently of Compose recomposition. Keep the
    // rendered absolute anchor stable even when one frame still has the previous placement.
    Box(Modifier.fillMaxSize().drawWithContent {
        val drawnPlacement = compactLayout?.invoke() ?: placement
        val resizing = hostView.width != drawnPlacement.width || hostView.height != drawnPlacement.height
        if (resizing) hostView.getLocationOnScreen(hostLocation)
        translate(left = if (resizing) (drawnPlacement.x - hostLocation[0]).toFloat() else 0f, top = if (resizing) (drawnPlacement.y - hostLocation[1]).toFloat() else 0f) {
            this@drawWithContent.drawContent()
        }
    }.then(scroll)) {
        val entrancesVisible by remember(expanded) { derivedStateOf { expanded || effects.value > .001f } }
        if (compactLayout == null && entrancesVisible) {
            val count = settings.shortcuts.size
            settings.shortcuts.forEachIndexed { index, item ->
                val slot = index - wheel.first + (5 - count).coerceAtLeast(0) / 2f
                val angle = (slot - 2) * 36f + wheelOffset / stepPx * 36f
                if (kotlin.math.abs(angle) > 136f && sorting != item.id) return@forEachIndexed
                val edgeAlpha = ((100f - kotlin.math.abs(angle)) / 28f).coerceIn(0f, 1f)
                // Alpha does not remove a Surface from hit testing. Hidden labels can
                // overlap the fifth visible icon at the mirrored end of the arc.
                if (edgeAlpha <= .01f && sorting != item.id) return@forEachIndexed
                key(item.id) {
                val held = sorting == item.id
                val radians = Math.toRadians(angle.toDouble())
                val dx = (if (settings.rightEdge) -1 else 1) * kotlin.math.cos(radians).toFloat() * radius
                val dy = kotlin.math.sin(radians).toFloat() * radius
                val x = with(density) { placement.fabX.toDp() } + dx.dp
                val y = with(density) { placement.fabY.toDp() } + dy.dp
                val settling = sorting != null || reorderMotion > .001f
                val displayedX: Dp
                val displayedY: Dp
                if (settling && !wheeling) {
                    val settledX by animateDpAsState(x, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "entrance-x")
                    val settledY by animateDpAsState(y, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "entrance-y")
                    displayedX = settledX; displayedY = settledY
                } else {
                    displayedX = x; displayedY = y
                }
                val pointerDelta = if (held) sortCenter?.let {
                    it - Offset(with(density) { displayedX.toPx() }, with(density) { displayedY.toPx() })
                } ?: Offset.Zero else Offset.Zero
                val releasedDelta by animateOffsetAsState(pointerDelta, if (held) snap() else MaterialTheme.motionScheme.defaultSpatialSpec(), label = "entrance-drop")
                val followDelta = if (held) pointerDelta else releasedDelta
                val labelLift = kotlin.math.sign(angle) * ((kotlin.math.abs(angle) - 36f) / 36f).coerceIn(0f, 1f) * 8f
                Row(Modifier.zIndex(if (held) 2f else edgeAlpha).offset {
                    if (settling && !wheeling) IntOffset((displayedX - 24.dp).roundToPx(), (displayedY - 24.dp).roundToPx())
                    else IntOffset((motion.center.x + dx.dp.toPx() * motion.progress - 24.dp.toPx()).roundToInt(),
                        (motion.center.y + dy.dp.toPx() * motion.progress - 24.dp.toPx()).roundToInt())
                }
                    .widthIn(max = 160.dp)
                    .graphicsLayer {
                        translationX = if (settings.rightEdge) -(size.width - 48.dp.toPx()) else 0f
                        translationX += followDelta.x
                        translationY = followDelta.y
                        transformOrigin = TransformOrigin(if (settings.rightEdge) 1f - 24.dp.toPx() / size.width else 24.dp.toPx() / size.width, .5f)
                        alpha = effects.value * if (held) 1f else edgeAlpha * if (item.enabled) 1f else .45f; scaleX = .45f + .55f * motion.progress; scaleY = scaleX
                    },
                    verticalAlignment = Alignment.CenterVertically) {
                    @Composable fun Label() {
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface, modifier = Modifier.offset { IntOffset(0, (labelLift.dp.toPx() * motion.progress).roundToInt()) }.widthIn(max = 104.dp)) {
                            Text(item.title, Modifier.padding(horizontal = 8.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    val showLabel = settings.showLabels || sorting == item.id
                    if (showLabel && settings.rightEdge) { Label(); Spacer(Modifier.width(8.dp)) }
                    IconActionHint(item.title, hasLongAction = true) {
                        FilledTonalIconButton(onClick = feedbackClick { if (item.enabled) { interact(); open(item) } }, enabled = expanded && edgeAlpha > .01f && item.enabled,
                            modifier = Modifier.size(48.dp).overlayEntranceReorder(item.id, expanded && (held || edgeAlpha > .01f),
                                { sorting = it; interact() }, reorder,
                                { wheel = wheel.copy(first = (wheel.first - 1).coerceAtLeast(0)) }, { sortCenter = it })
                                .semantics { contentDescription = item.title; if (edgeAlpha <= .01f && !held) hideFromAccessibility() },
                            shapes = IconButtonDefaults.shapes(shape = CircleShape, pressedShape = MaterialTheme.shapes.medium)) {
                            OverlayShortcutIcon(item)
                        }
                    }
                    if (showLabel && !settings.rightEdge) { Spacer(Modifier.width(8.dp)); Label() }
                }
                }
            }
        }
        OverlayFab(motion, expanded, settings.rightEdge, retraction, edgeRetraction != null, effects,
            toggle, move, drop, configure)
    }
}

@Composable internal fun OverlayBubbleContent(notice: OverlayNotice, dismiss: () -> Unit, open: (OverlayShortcut) -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer, shadowElevation = 6.dp, modifier = Modifier.padding(8.dp)) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(notice.text, Modifier.weight(1f).padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = feedbackClick(dismiss), modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Close, uiText("overlay.bubble.close"), Modifier.size(18.dp)) }
            }
            notice.shortcut?.let { item ->
                FilledTonalButton(onClick = feedbackClick { open(item) }) {
                    Icon(MaterialSymbols.vector(notice.symbol) ?: Icons.Default.Extension, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(notice.label.orEmpty())
                }
            }
        }
    }
}
