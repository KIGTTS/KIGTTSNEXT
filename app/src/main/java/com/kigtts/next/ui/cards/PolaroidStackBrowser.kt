package com.kigtts.next.ui.cards

import androidx.compose.animation.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
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
    val latestColumns = rememberUpdatedState(gridColumns); val columnsChanged = rememberUpdatedState(changeColumns)
    val management = LocalCardManagementGeometry.current
    val managePhoto: (PolaroidCard) -> Unit = { card -> management?.begin(card.id); onManage(card) }
    val haptics = rememberUpdatedState(LocalHapticFeedback.current)
    Box(modifier.testTag("polaroid-browser").then(if (enabled) Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var scale = 1f; var pinching = false; var switched = false
            do {
                val event = awaitPointerEvent()
                if (event.changes.count { it.pressed } > 1) {
                    pinching = true
                    scale *= event.calculateZoom()
                    val target = pinchBrowseScale(latestMode.value, latestColumns.value, scale)
                    if (!switched && (target.mode != latestMode.value || target.columns != latestColumns.value)) {
                        switched = true
                        haptics.value.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        switch.value(target.mode); columnsChanged.value(target.columns)
                    }
                    event.changes.forEach { it.consume() }
                } else if (pinching) event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    } else Modifier)) {
        AnimatedContent(mode, modifier = Modifier.fillMaxSize().polaroidAlbumDrawingClip(), label = "polaroid-browse-mode",
            transitionSpec = { (fadeIn() togetherWith fadeOut()).using(null) }) { target ->
            if (target == PolaroidBrowseMode.ALBUMS) PolaroidAlbumOverview(albums, albumCards, selectedAlbumId ?: albumContaining(albums, focusedId)?.id,
                    imageFile, frames, enabled,
                    choose = { id -> chooseAlbum(id); columnsChanged.value(4); switch.value(PolaroidBrowseMode.GRID) }, manage = onManageAlbum)
            else PolaroidAlbumTransition(albumChangeRequest,
                AlbumBrowseSnapshot(cards, gridCards, focusedId, selectedAlbumId, positionRequest), albums) { scene, current ->
                val interactive = enabled && current
                when {
                    target == PolaroidBrowseMode.STACK && scene.cards.isNotEmpty() -> PolaroidStack(scene.cards, scene.focusedId, imageFile, frames,
                        interactive, { if (current) focus(it) }, { if (current) preview(it) }, { if (current) managePhoto(it) },
                        scene.positionRequest, { if (current) onPositioned(it) }, wideLayout, current)
                    target == PolaroidBrowseMode.GRID && scene.gridCards.isNotEmpty() -> PolaroidGallery(scene.gridCards, imageFile, frames, interactive,
                        { if (current) { focus(it); preview(it) } }, { if (current) managePhoto(it) }, gridColumns, current)
                    else -> PolaroidEmpty()
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
    LaunchedEffect(focusedId, cards, positionRequest) {
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
        val drawingPadding = 208.dp
        HorizontalPager(pager, Modifier.fillMaxSize().polaroidPagerDrawingSpace(drawingPadding),
            contentPadding = PaddingValues(vertical = drawingPadding),
            key = { latestCards.value.getOrNull(it)?.id ?: "pending/card/" + it }, beyondViewportPageCount = 2,
            userScrollEnabled = enabled, pageSpacing = 0.dp) { index ->
            val card = latestCards.value.getOrNull(index) ?: return@HorizontalPager
            val frame = frames.firstOrNull { it.id == card.frameStyleId }
            val width = polaroidBrowseWidth(maxWidth, maxHeight, polaroidFormat(card, frame).geometry.aspectRatio.toFloat(), wide)
            val resize = rememberPolaroidResize(card.id, width, maxWidth, maxHeight, coordinateSpace = wide)
            // Read pager offsets during placement/layer updates, leaving photo decoding and canvas
            // composition unchanged on animation frames. Album order fixes the drawing order:
            // a preceding card exits fully before a following card becomes the foreground.
            Box(Modifier.fillMaxSize().layout { measurable, constraints ->
                val placed = measurable.measure(constraints)
                layout(placed.width, placed.height) {
                    val relative = index - pager.currentPage - pager.currentPageOffsetFraction
                    val distance = relative.absoluteValue
                    placed.placeWithLayer(0, 0, zIndex = -index.toFloat()) {
                        translationX = if (wide) relative * (polaroidFanSpacing(size.width, width.toPx()) - size.width)
                            else if (relative >= 0f) -relative * size.width * .92f else relative * size.width * .05f
                        scaleX = (1f - distance * (if (wide) .035f else .07f)).coerceAtLeast(.82f); scaleY = scaleX
                        rotationZ = (relative * (if (wide) 4f else 2.5f)).coerceIn(if (wide) -8f else -5f, if (wide) 8f else 5f)
                        // Paper stays opaque: overlapping captions must never ghost through it.
                        // Only a distant page that is leaving the stack fades away.
                        alpha = if (distance <= 2f) 1f else (3f - distance).coerceIn(0f, 1f)
                    }
                }
            }, contentAlignment = Alignment.Center) {
                val foreground = latestCards.value.indexOfFirst { it.id == focusedId }.takeIf { it >= 0 } ?: pager.settledPage
                // A reorder or explicit album jump can move the stable focus before the old
                // page settles. Keep that currently painted page's image while it exits.
                val visibleImages: (String) -> File? = if (index in foreground - (if (wide) 2 else 1)..foreground + 2 ||
                    index == pager.currentPage || index == pager.settledPage) imageFile else { _ -> null }
                PolaroidCanvas(card, visibleImages, Modifier.width(width).polaroidResizeLayer(resize).cardManagementSource(card.id, registerManagementSource)
                    .combinedClickable(enabled = enabled,
                        onClick = feedbackClick { if (pager.currentPage == index) preview(card) },
                        onLongClick = feedbackClick { if (pager.currentPage == index) edit(card) }),
                    frame = frame, decodeMaximum = if (index == foreground) 1200 else 256)
            }
        }
        Text((pager.settledPage + 1).toString() + " / " + cards.size,
            Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp).testTag("cards-position"),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PolaroidGallery(cards: List<PolaroidCard>, imageFile: (String) -> File?, frames: List<PolaroidFrameStyle>,
    enabled: Boolean, preview: (PolaroidCard) -> Unit, edit: (PolaroidCard) -> Unit, columns: Int, registerManagementSource: Boolean) {
    LazyVerticalGrid(GridCells.Fixed(columns.coerceIn(2, 4)), Modifier.fillMaxSize().clipToBounds().testTag("cards-grid")
        .semantics { stateDescription = columns.toString() + " 列" },
        contentPadding = PaddingValues(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 100.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items(cards, key = { it.id }) { card ->
            BoxWithConstraints(Modifier.fillMaxWidth().animateItem()) {
                val frame = frames.firstOrNull { it.id == card.frameStyleId }
                val height = maxWidth / polaroidFormat(card, frame).geometry.aspectRatio.toFloat()
                val resize = rememberPolaroidResize(card.id, maxWidth, maxWidth, height, coordinateSpace = LocalCardWindowLandscape.current)
                PolaroidCanvas(card, imageFile, Modifier.fillMaxWidth().polaroidResizeLayer(resize).cardManagementSource(card.id, registerManagementSource)
                    .combinedClickable(enabled = enabled, onClick = feedbackClick { preview(card) }, onLongClick = feedbackClick { edit(card) }), frame = frame)
            }
        }
    }
}
