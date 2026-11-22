package com.kigtts.next.ui.cards

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.plugin.api.*
import java.io.File

private class GridEntranceSnapshot(var columns: Int) {
    var visible: Map<String, Int> = emptyMap()
}

/** Retain visible photos, then reveal the extra slots in reading order when the grid gets denser. */
@Composable
internal fun PolaroidGallery(cards: List<PolaroidCard>, imageFile: (String) -> File?, frames: List<PolaroidFrameStyle>,
    enabled: Boolean, preview: (PolaroidCard) -> Unit, edit: (PolaroidCard) -> Unit, columns: Int,
    registerManagementSource: Boolean, focusedId: String?) {
    val grid = rememberLazyGridState(initialFirstVisibleItemIndex = cards.indexOfFirst { it.id == focusedId }.coerceAtLeast(0) / columns * columns)
    val previous = remember { GridEntranceSnapshot(columns) }
    val sourceGeometry = LocalCardManagementGeometry.current
    val arriving = remember { sourceGeometry?.sources?.keys?.toSet().orEmpty() }
    val retained = remember(columns) {
        (previous.visible.takeIf { columns > previous.columns && it.isNotEmpty() }
            ?: arriving.takeIf { it.isNotEmpty() && previous.visible.isEmpty() }?.associateWith { id -> cards.indexOfFirst { it.id == id } })
            .also { previous.columns = columns }
    }
    val reveal = remember(columns) { Animatable(if (retained == null) 1f else 0f) }
    LaunchedEffect(reveal) { reveal.animateTo(1f, tween(600, easing = LinearEasing)) }
    LaunchedEffect(grid) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.associate { it.key.toString() to it.index } }
            .collect { previous.visible = it }
    }
    val firstNew = retained?.values?.maxOrNull()?.plus(1) ?: 0
    val anchors = LocalPolaroidPinchAnchors.current
    val pending = anchors?.pending?.takeIf { it.columns == columns }
    val density = LocalDensity.current
    val columnDescription = com.kigtts.next.i18n.uiText("photo.grid_columns", "count" to columns.toString())
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val index = pending?.let { request -> cards.indexOfFirst { it.id == request.id } } ?: -1
        if (index >= 0 && pending != null) {
            val card = cards[index]
            val frame = frames.firstOrNull { it.id == card.frameStyleId }
            val paperWidth = (maxWidth - 36.dp - 14.dp * (columns - 1)) / columns
            val paperHeight = paperWidth / com.kigtts.next.cards.polaroidPaperGeometry(card, frame).aspectRatio.toFloat()
            val clearance = LocalCardHeaderClearance.current.coerceAtLeast(0.dp)
            val offset = with(density) {
                (paperHeight.toPx() * pending.paperY - maxHeight.toPx() * pending.viewportY + clearance.toPx() + 18.dp.toPx()).roundToInt()
            }
            SideEffect {
                grid.requestScrollToItem(index, offset)
                if (anchors?.pending === pending) anchors.pending = null
            }
        }
        LazyVerticalGrid(GridCells.Fixed(columns.coerceIn(2, 6)),
            Modifier.fillMaxSize().padding(top = LocalCardHeaderClearance.current.coerceAtLeast(0.dp)).testTag("cards-grid")
                .semantics { stateDescription = columnDescription }, state = grid,
            contentPadding = PaddingValues(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 100.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            itemsIndexed(cards, key = { _, card -> card.id }) { index, card ->
                BoxWithConstraints(Modifier.fillMaxWidth().animateItem(fadeInSpec = null, fadeOutSpec = null,
                    placementSpec = tween(240, easing = FastOutSlowInEasing))) {
                    val frame = frames.firstOrNull { it.id == card.frameStyleId }
                    val height = maxWidth / com.kigtts.next.cards.polaroidPaperGeometry(card, frame).aspectRatio.toFloat()
                    val resize = rememberPolaroidResize(card.id, maxWidth, maxWidth, height, coordinateSpace = LocalCardWindowLandscape.current)
                    PolaroidCanvas(card, imageFile, Modifier.fillMaxWidth().polaroidResizeLayer(resize)
                        .polaroidShadowFade {
                            if (retained == null || card.id in retained) 1f else {
                                val delay = ((index - firstNew).coerceAtLeast(0) * 32).coerceAtMost(160)
                                // Finish making room before newly visible papers fill the extra slots.
                                FastOutSlowInEasing.transform(((reveal.value * 600f - 240f - delay) / 200f).coerceIn(0f, 1f))
                            }
                        }.cardManagementSource(card.id, registerManagementSource)
                        .polaroidPinchTarget(card.id, PolaroidBrowseMode.GRID, registerManagementSource)
                        .combinedClickable(enabled = enabled, onClick = feedbackClick { preview(card) }, onLongClick = feedbackClick { edit(card) }), frame = frame)
                }
            }
        }
    }
}
