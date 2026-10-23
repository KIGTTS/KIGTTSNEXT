@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package com.kigtts.next.ui.cards

import androidx.compose.animation.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.plugin.api.*
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.File
import kotlin.math.absoluteValue

@Composable
internal fun PolaroidBrowser(cards: List<PolaroidCard>, mode: PolaroidBrowseMode, focusedId: String?,
    imageFile: (String) -> File?, frames: List<PolaroidFrameStyle>, enabled: Boolean,
    changeMode: (PolaroidBrowseMode) -> Unit, focus: (PolaroidCard) -> Unit, preview: (PolaroidCard) -> Unit,
    edit: (PolaroidCard) -> Unit, modifier: Modifier = Modifier, gridColumns: Int = 2, changeColumns: (Int) -> Unit = {},
    gridCards: List<PolaroidCard> = cards, positionRequest: Long = 0L, onPositioned: (Long) -> Unit = {},
    albums: List<PolaroidAlbum> = emptyList(), albumCards: List<PolaroidCard> = cards, chooseAlbum: (String) -> Unit = {},
    selectedAlbumId: String? = null, wideLayout: Boolean? = null, albumChangeRequest: Long = 0L,
    onManage: (PolaroidCard) -> Unit = edit, onManageAlbum: (String) -> Unit = {}) {
    val latestMode = rememberUpdatedState(mode); val switch = rememberUpdatedState(changeMode)
    val minimum = if (wideLayout ?: LocalCardWindowLandscape.current ?: false) 4 else 2
    val maximum = minimum + 2
    val columns = gridColumns.coerceIn(minimum, maximum)
    val latestColumns = rememberUpdatedState(columns); val columnsChanged = rememberUpdatedState(changeColumns)
    val limits = rememberUpdatedState(minimum to maximum)
    val management = LocalCardManagementGeometry.current
    val managePhoto: (PolaroidCard) -> Unit = { card -> management?.begin(card.id); onManage(card) }
    val haptics = rememberUpdatedState(LocalHapticFeedback.current)
    val pinchAnchors = remember { PolaroidPinchAnchors() }
    val pinchCards = rememberUpdatedState(cards + gridCards)
    val pinchFocus = rememberUpdatedState(focus)
    val currentFocus = rememberUpdatedState(focusedId)
    CompositionLocalProvider(LocalPolaroidPinchAnchors provides pinchAnchors) {
    Box(modifier.testTag("polaroid-browser").onGloballyPositioned { pinchAnchors.browser = it }.then(if (enabled) Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var scale = 1f; var pinching = false; var switched = false
            do {
                val event = awaitPointerEvent()
                if (event.changes.count { it.pressed } > 1) {
                    pinching = true
                    scale *= event.calculateZoom()
                    val target = pinchBrowseScale(latestMode.value, latestColumns.value, scale, limits.value.first, limits.value.second)
                    if (!switched && (target.mode != latestMode.value || target.columns != latestColumns.value)) {
                        switched = true
                        val anchor = pinchAnchors.at(event.calculateCentroid(), latestMode.value, currentFocus.value, target.columns)
                        anchor?.let { selected ->
                            pinchCards.value.firstOrNull { it.id == selected.id }?.let(pinchFocus.value)
                            pinchAnchors.pending = selected.takeIf { target.mode == PolaroidBrowseMode.GRID }
                        }
                        haptics.value.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        switch.value(target.mode); columnsChanged.value(target.columns)
                    }
                    event.changes.forEach { it.consume() }
                } else if (pinching) event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    } else Modifier)) {
        val pageVisible = LocalPolaroidPhotoVisible.current
        AnimatedContent(mode, modifier = Modifier.fillMaxSize().polaroidAlbumDrawingClip(), label = "polaroid-browse-mode",
            transitionSpec = { (fadeIn(androidx.compose.animation.core.tween(340)) togetherWith fadeOut(androidx.compose.animation.core.tween(340))).using(null) }) { target ->
          CompositionLocalProvider(LocalPolaroidPhotoVisible provides (pageVisible && mode == target),
              LocalPolaroidEntering provides (transition.currentState == EnterExitState.PreEnter),
              LocalPolaroidSharedScope provides LocalPolaroidSharedScope.current.takeUnless { target == PolaroidBrowseMode.ALBUMS }) {
            if (target == PolaroidBrowseMode.ALBUMS) PolaroidAlbumOverview(albums, albumCards, selectedAlbumId ?: albumContaining(albums, focusedId)?.id,
                    imageFile, frames, enabled,
                    choose = { id -> chooseAlbum(id); columnsChanged.value(maximum); switch.value(PolaroidBrowseMode.GRID) }, manage = onManageAlbum)
            else PolaroidAlbumTransition(albumChangeRequest,
                AlbumBrowseSnapshot(cards, gridCards, focusedId, selectedAlbumId, positionRequest), albums) { scene, current ->
                val interactive = enabled && current
                CompositionLocalProvider(LocalPolaroidPhotoVisible provides (pageVisible && mode == target && current)) {
                when {
                    target == PolaroidBrowseMode.STACK && scene.cards.isNotEmpty() -> PolaroidStack(scene.cards, scene.focusedId, imageFile, frames,
                        interactive, { if (current) focus(it) }, { if (current) preview(it) }, { if (current) managePhoto(it) },
                        scene.positionRequest, { if (current) onPositioned(it) }, wideLayout, current)
                    target == PolaroidBrowseMode.GRID && scene.gridCards.isNotEmpty() -> PolaroidGallery(scene.gridCards, imageFile, frames, interactive,
                        { if (current) { focus(it); preview(it) } }, { if (current) managePhoto(it) }, columns, current, scene.focusedId)
                    else -> PolaroidEmpty()
                }
                }
            }
          }
        }
    }
    }
}

@Composable
private fun PolaroidEmpty() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Collections, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("从一张照片开始", style = MaterialTheme.typography.bodyLarge)
            Text("下滑拍照，也可在相机中选图", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PolaroidStack(cards: List<PolaroidCard>, focusedId: String?, imageFile: (String) -> File?,
    frames: List<PolaroidFrameStyle>, enabled: Boolean, focus: (PolaroidCard) -> Unit,
    preview: (PolaroidCard) -> Unit, edit: (PolaroidCard) -> Unit, positionRequest: Long, onPositioned: (Long) -> Unit,
    wideLayout: Boolean?, registerManagementSource: Boolean) {
    val latestCards = rememberUpdatedState(cards); val focusAction = rememberUpdatedState(focus)
    val positioned = rememberUpdatedState(onPositioned)
    // Count, keys and page content read one snapshot. A list change must not combine a new
    // count with an old captured key/content lambda during LazyLayout's invalidation.
    val pager = rememberPagerState(initialPage = cards.indexOfFirst { it.id == focusedId }.coerceAtLeast(0)) { latestCards.value.size }
    var positioning by remember { mutableStateOf(false) }
    var positionVersion by remember { mutableLongStateOf(0L) }
    LaunchedEffect(pager) {
        var wasScrolling = false
        snapshotFlow { pager.isScrollInProgress to pager.settledPage }.distinctUntilChanged().collect { (scrolling, index) ->
            // A catalog's insert/reorder can relocate a stable key without a user page change.
            // Publish focus only after a real scroll settles, preserving the selected card ID.
            if (!scrolling && wasScrolling && !positioning) latestCards.value.getOrNull(index)?.let { focusAction.value(it) }
            wasScrolling = scrolling
        }
    }
    // Metadata/thumbnail updates must not seize an active finger or cancel its fling.
    // The stable page key already follows ordinary catalog reordering.
    LaunchedEffect(focusedId, positionRequest) {
        val target = cards.indexOfFirst { it.id == focusedId }
        if (target >= 0 && (target != pager.settledPage || pager.isScrollInProgress)) {
            val version = ++positionVersion
            positioning = true
            try {
                pager.positionPolaroid(target)
                positioned.value(positionRequest)
            } finally { if (positionVersion == version) positioning = false }
        } else if (target >= 0) positioned.value(positionRequest)
    }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("cards-stack")) {
        val wide = wideLayout ?: (maxWidth > maxHeight)
        val backdrop = MaterialTheme.colorScheme.background
        val carouselViewportWidth = maxWidth.value
        val carouselWidths = remember(cards, frames, maxWidth, maxHeight, wide) {
            if (wide) cards.map { card ->
                polaroidBrowseWidth(maxWidth, maxHeight,
                    polaroidFormat(card, frames.firstOrNull { it.id == card.frameStyleId }).geometry.aspectRatio.toFloat(), true).value
            } else emptyList()
        }
        val drawingPadding = 416.dp
        HorizontalPager(pager, Modifier.fillMaxSize().polaroidPagerDrawingSpace(drawingPadding),
            contentPadding = PaddingValues(bottom = drawingPadding),
            key = { latestCards.value.getOrNull(it)?.id ?: "pending/card/" + it }, beyondViewportPageCount = if (wide) 3 else 2,
            userScrollEnabled = enabled, pageSpacing = 0.dp) { index ->
            val card = latestCards.value.getOrNull(index) ?: return@HorizontalPager
            val frame = frames.firstOrNull { it.id == card.frameStyleId }
            val width = polaroidBrowseWidth(maxWidth, maxHeight, polaroidFormat(card, frame).geometry.aspectRatio.toFloat(), wide)
            val resize = rememberPolaroidResize(card.id, width, maxWidth, maxHeight, coordinateSpace = wide)
            // Read pager offsets during placement/layer updates, leaving photo decoding and canvas
            // composition unchanged on animation frames. Landscape fits the actual paper widths;
            // portrait keeps the overlapping paper stack in album order.
            Box(Modifier.fillMaxSize().layout { measurable, constraints ->
                val placed = measurable.measure(constraints)
                layout(placed.width, placed.height) {
                    val relative = index - pager.currentPage - pager.currentPageOffsetFraction
                    val distance = relative.absoluteValue
                    placed.placeWithLayer(0, 0, zIndex = if (wide) -distance else -index.toFloat()) {
                        if (wide) {
                            val pose = carouselPhotoPose(index, pager.currentPage + pager.currentPageOffsetFraction, carouselWidths, carouselViewportWidth)
                            translationX = pose.x * density - relative * size.width
                            scaleX = pose.scale; scaleY = scaleX
                        } else {
                            translationX = if (relative >= 0f) -relative * size.width * .92f else relative * size.width * .05f
                            scaleX = (1f - distance * .07f).coerceAtLeast(.82f); scaleY = scaleX
                        }
                    }
                }
            }, contentAlignment = Alignment.Center) {
                val foreground = latestCards.value.indexOfFirst { it.id == focusedId }.takeIf { it >= 0 } ?: pager.settledPage
                // Pager composition bounds preloading. Never blank an already composed paper
                // based on the last settled focus while a rapid swipe exposes its neighbors.
                CompositionLocalProvider(LocalPolaroidOverlayOrder provides (1000f - kotlin.math.abs(index - foreground)),
                    LocalPolaroidVisualPose provides {
                        val relative = index - pager.currentPage - pager.currentPageOffsetFraction
                        if (wide) PolaroidVisualPose() else PolaroidVisualPose(
                            (relative * 2.5f).coerceIn(-5f, 5f), (3f - relative.absoluteValue).coerceIn(0f, 1f))
                    }) {
                PolaroidCanvas(card, imageFile, Modifier.width(width).polaroidResizeLayer(resize).cardManagementSource(card.id, registerManagementSource)
                    .polaroidPinchTarget(card.id, PolaroidBrowseMode.STACK, registerManagementSource)
                    .then(if (wide) Modifier.polaroidShadowFade {
                        carouselPhotoPose(index, pager.currentPage + pager.currentPageOffsetFraction, carouselWidths, carouselViewportWidth).alpha
                    }.drawBehind {
                        val pose = carouselPhotoPose(index, pager.currentPage + pager.currentPageOffsetFraction, carouselWidths, carouselViewportWidth)
                        // Fade into the page color, so stacked photos do not ghost through one another.
                        drawRoundRect(backdrop.copy(alpha = (pose.alpha / .22f).coerceIn(0f, 1f)), cornerRadius = CornerRadius(6.dp.toPx()))
                    } else Modifier)
                    .combinedClickable(enabled = enabled,
                        onClick = feedbackClick { if (pager.currentPage == index) preview(card) },
                        onLongClick = feedbackClick { if (pager.currentPage == index) edit(card) }),
                    frame = frame, decodeMaximum = if (index == foreground) 1200 else 256)
                }
            }
        }
        Text((pager.settledPage + 1).toString() + " / " + cards.size,
            Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp).testTag("cards-position"),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
