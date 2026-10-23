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
    LazyVerticalGrid(GridCells.Fixed(columns.coerceIn(2, 6)),
        Modifier.fillMaxSize().padding(top = LocalCardHeaderClearance.current.coerceAtLeast(0.dp)).testTag("cards-grid")
            .semantics { stateDescription = "$columns 列" }, state = grid,
        contentPadding = PaddingValues(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 100.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        itemsIndexed(cards, key = { _, card -> card.id }) { index, card ->
            BoxWithConstraints(Modifier.fillMaxWidth().animateItem(fadeInSpec = null, fadeOutSpec = null,
                placementSpec = tween(240, easing = FastOutSlowInEasing))) {
                val frame = frames.firstOrNull { it.id == card.frameStyleId }
                val height = maxWidth / polaroidFormat(card, frame).geometry.aspectRatio.toFloat()
                val resize = rememberPolaroidResize(card.id, maxWidth, maxWidth, height, coordinateSpace = LocalCardWindowLandscape.current)
                PolaroidCanvas(card, imageFile, Modifier.fillMaxWidth().polaroidResizeLayer(resize)
                    .polaroidShadowFade {
                        if (retained == null || card.id in retained) 1f else {
                            val delay = ((index - firstNew).coerceAtLeast(0) * 32).coerceAtMost(160)
                            // Finish making room before newly visible papers fill the extra slots.
                            FastOutSlowInEasing.transform(((reveal.value * 600f - 240f - delay) / 200f).coerceIn(0f, 1f))
                        }
                    }.cardManagementSource(card.id, registerManagementSource)
                    .combinedClickable(enabled = enabled, onClick = feedbackClick { preview(card) }, onLongClick = feedbackClick { edit(card) }), frame = frame)
            }
        }
    }
}
