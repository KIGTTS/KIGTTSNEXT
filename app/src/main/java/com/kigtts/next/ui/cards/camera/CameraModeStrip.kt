package com.kigtts.next.ui.cards.camera

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.keepColorsWhenLocked
import com.kigtts.next.ui.navigation.excludeCameraPull
import kotlinx.coroutines.delay

/** The fixed page pitch keeps the selected mode at the shutter axis, including the two ends. */
@Composable internal fun CameraModeStrip(mode: CameraMode, change: (CameraMode) -> Unit, enabled: Boolean,
    vertical: Boolean, allowVideo: Boolean = true) {
    val modes = remember(allowVideo) { CameraMode.entries.filter { allowVideo || !it.recordsVideo } }
    val pager = rememberPagerState(initialPage = modes.indexOf(mode).coerceAtLeast(0)) { modes.size }
    val latestChange by rememberUpdatedState(change)
    val latestMode by rememberUpdatedState(mode)
    val feedback by rememberUpdatedState(LocalHapticFeedback.current)
    var wake by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf(true) }
    LaunchedEffect(pager) {
        snapshotFlow { pager.isScrollInProgress to pager.settledPage }.collect { (moving, page) ->
            if (!moving) {
                modes.getOrNull(page)?.takeIf { it != latestMode }?.let {
                    latestChange(it)
                }
            }
        }
    }
    LaunchedEffect(pager, modes) {
        var previous = pager.currentPage
        snapshotFlow { pager.currentPage }.collect { page ->
            if (page != previous && pager.isScrollInProgress) {
                feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
            previous = page
        }
    }
    LaunchedEffect(mode, modes) {
        val target = modes.indexOf(mode)
        if (target >= 0 && target != pager.currentPage) pager.animateScrollToPage(target, animationSpec = tween(220))
    }
    LaunchedEffect(mode, wake, pager.isScrollInProgress) {
        expanded = true
        if (!pager.isScrollInProgress) { delay(2_600); expanded = false }
    }
    val colors = MaterialTheme.colorScheme
    val overlay = LocalCameraShutterOverlay.current
    val active = if (overlay && colors.primary.luminance() < colors.inversePrimary.luminance()) colors.inversePrimary else colors.primary
    val idle = if (overlay) Color.White.copy(alpha = .84f) else colors.onSurfaceVariant
    val capsule by animateColorAsState(if (expanded) Color.Transparent else if (overlay) Color.Black.copy(alpha = .38f)
        else colors.surfaceContainerHighest, tween(220), label = "camera-mode-capsule")
    BoxWithConstraints(if (vertical) Modifier.width(72.dp).fillMaxHeight() else Modifier.fillMaxWidth().height(48.dp),
        contentAlignment = Alignment.Center) {
        val available = if (vertical) maxHeight else maxWidth
        val extent by animateDpAsState(if (expanded) available else minOf(104.dp, available), tween(220), label = "camera-mode-extent")
        val padding = ((available - 96.dp) / 2).coerceAtLeast(0.dp)
        val strip = (if (vertical) Modifier.width(48.dp).height(extent) else Modifier.width(extent).height(48.dp))
            .testTag("camera-mode-strip").excludeCameraPull().clip(RoundedCornerShape(24.dp)).background(capsule)
            .pointerInput(enabled) {
                if (enabled) awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    expanded = true; wake++
                    waitForUpOrCancellation(pass = PointerEventPass.Initial)
                    wake++
                }
            }
        val item: @Composable PagerScope.(Int) -> Unit = { page ->
            val value = modes[page]
            val ink by animateColorAsState(if (value == mode) active else idle, tween(180), label = "camera-mode-ink")
            TextButton(feedbackClick { wake++; latestChange(value) }, Modifier.fillMaxSize()
                .testTag("camera-mode/${value.name}").semantics { selected = value == mode }, enabled = enabled,
                contentPadding = PaddingValues(horizontal = 4.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = ink).keepColorsWhenLocked()) {
                Text(value.label, if (vertical) Modifier.verticalModeLabel() else Modifier,
                    style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
            }
        }
        // Reveal/crop the full-size pager instead of resizing its viewport during a held gesture.
        Box(strip, contentAlignment = Alignment.Center) {
            if (vertical) VerticalPager(pager, Modifier.width(48.dp).requiredHeight(available),
                contentPadding = PaddingValues(vertical = padding), pageSize = PageSize.Fixed(96.dp),
                pageSpacing = 8.dp, userScrollEnabled = enabled, pageContent = item)
            else HorizontalPager(pager, Modifier.requiredWidth(available).height(48.dp),
                contentPadding = PaddingValues(horizontal = padding), pageSize = PageSize.Fixed(96.dp),
                pageSpacing = 8.dp, userScrollEnabled = enabled, pageContent = item)
        }
    }
}

private fun Modifier.verticalModeLabel(): Modifier = layout { label, bounds ->
    val text = label.measure(Constraints(minWidth = bounds.minHeight, maxWidth = bounds.maxHeight,
        minHeight = bounds.minWidth, maxHeight = bounds.maxWidth))
    layout(text.height, text.width) {
        text.placeWithLayer((text.height - text.width) / 2, (text.width - text.height) / 2) { rotationZ = -90f }
    }
}
