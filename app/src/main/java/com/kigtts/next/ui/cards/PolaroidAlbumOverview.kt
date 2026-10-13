package com.kigtts.next.ui.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.plugin.api.*
import java.io.File

/** Every album has an entry, including empty albums. Covers retain each paper's actual ratio. */
@Composable
internal fun PolaroidAlbumOverview(albums: List<PolaroidAlbum>, cards: List<PolaroidCard>, selectedId: String?,
    imageFile: (String) -> File?, frames: List<PolaroidFrameStyle>, enabled: Boolean, choose: (String) -> Unit) {
    val byId = remember(cards) { cards.associateBy { it.id } }
    LazyVerticalGrid(GridCells.Adaptive(152.dp), Modifier.fillMaxSize().testTag("cards-album-overview"),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 88.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items(albums, key = { it.id }) { album ->
            val cover = album.cardIds.mapNotNull(byId::get).take(3)
            Card(onClick = feedbackClick { choose(album.id) }, enabled = enabled,
                modifier = Modifier.fillMaxWidth().animateItem().testTag("polaroid-album/" + album.id)
                    .semantics { selected = album.id == selectedId },
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = if (album.id == selectedId)
                    MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
                AlbumCover(cover, imageFile, frames)
                Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                    Text(album.name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium)
                    Text(album.cardIds.count { it in byId }.toString() + " 张照片", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AlbumCover(cards: List<PolaroidCard>, imageFile: (String) -> File?, frames: List<PolaroidFrameStyle>) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(168.dp).padding(12.dp), contentAlignment = Alignment.Center) {
        if (cards.isEmpty()) Icon(Icons.Default.PhotoAlbum, "空相册", Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        else cards.forEachIndexed { index, card ->
            val frame = frames.firstOrNull { it.id == card.frameStyleId }
            val width = minOf(maxWidth * .7f, maxHeight * .88f * polaroidFormat(card, frame).geometry.aspectRatio.toFloat())
            PolaroidCanvas(card, imageFile, Modifier.width(width).zIndex((3 - index).toFloat()).graphicsLayer {
                translationX = when (index) { 1 -> 8.dp.toPx(); 2 -> -8.dp.toPx(); else -> 0f }
                rotationZ = when (index) { 1 -> 6f; 2 -> -8f; else -> -2f }
            }, frame = frame, decodeMaximum = 256)
        }
    }
}
