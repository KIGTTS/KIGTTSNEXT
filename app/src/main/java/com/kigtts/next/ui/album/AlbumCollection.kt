package com.kigtts.next.ui.album

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kigtts.next.media.AlbumMedia
import com.kigtts.next.ui.components.feedbackClick
import java.text.Collator
import java.util.Locale

internal data class AlbumCollection(val id: String, val name: String, val media: List<AlbumMedia>)

internal fun albumCollections(media: List<AlbumMedia>, allLabel: String): List<AlbumCollection> {
    val collator = Collator.getInstance()
    val folders = media.groupBy { it.bucketId }.map { (id, items) ->
        AlbumCollection("folder/$id", items.first().bucketName.ifBlank { "其他" }, items)
    }.sortedWith(compareBy<AlbumCollection> { albumFolderPriority(it.name, it.id) }
        .thenByDescending { it.media.maxOfOrNull(AlbumMedia::addedAt) ?: 0L }
        .thenComparator { a, b -> collator.compare(a.name, b.name) })
    val favorites = media.filter(AlbumMedia::favorite).takeIf { it.isNotEmpty() }
        ?.let { listOf(AlbumCollection("favorites", "收藏夹", it)) }.orEmpty()
    return listOf(AlbumCollection("all", allLabel, media)) + folders.filter { albumFolderPriority(it.name, it.id) < 2 } +
        favorites + folders.filter { albumFolderPriority(it.name, it.id) >= 2 }
}

private fun albumFolderPriority(name: String, id: String): Int = when {
    name.equals("KIGTTS", true) -> 0
    name.lowercase(Locale.ROOT) in setOf("camera", "相机", "dcim", "100andro") ||
        id.contains("/DCIM/Camera", true) -> 1
    name.lowercase(Locale.ROOT) in setOf("favorites", "favourites", "收藏", "收藏夹") -> 2
    name.lowercase(Locale.ROOT) in setOf("qq", "qq_images", "mobileqq", "qqfile_recv") -> 3
    else -> 4
}

@Composable
internal fun AlbumTitle(album: AlbumCollection, expanded: Boolean, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = "切换相册",
        onClick = feedbackClick(onClick)).padding(vertical = 8.dp).testTag("album-title"),
        verticalAlignment = Alignment.CenterVertically) {
        Text(album.name, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown, null)
    }
}

@Composable
internal fun AlbumCollectionGrid(albums: List<AlbumCollection>, selectedId: String, modifier: Modifier = Modifier,
    onPinchOpen: (AlbumCollection) -> Unit,
    onSelect: (AlbumCollection) -> Unit) {
    val state = rememberLazyGridState()
    val pinch by rememberUpdatedState<(Boolean, androidx.compose.ui.geometry.Offset) -> Boolean> { shrink, center ->
        val item = if (!shrink) state.layoutInfo.visibleItemsInfo.nearestAlbumItem(center) else null
        item?.let { albums.getOrNull(it.index) }?.let(onPinchOpen)
        item != null
    }
    LazyVerticalGrid(GridCells.Adaptive(148.dp), modifier.testTag("album-collections")
        .albumGridZoom { shrink, center -> pinch(shrink, center) }, state = state,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(albums, key = { it.id }) { album ->
            Surface(onClick = feedbackClick { onSelect(album) }, shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .96f)) {
                Column {
                    Box(Modifier.aspectRatio(1.28f), contentAlignment = Alignment.Center) {
                        val cover = album.media.firstOrNull()
                        if (cover != null) AlbumImage(cover, Modifier.fillMaxSize())
                        else Icon(Icons.Default.PhotoLibrary, null)
                        if (album.id == selectedId) Surface(Modifier.align(Alignment.TopEnd).padding(8.dp),
                            shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                            Icon(Icons.Default.CheckCircle, "当前相册", Modifier.padding(4.dp).size(20.dp))
                        }
                    }
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                        Text(album.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${album.media.size} 项", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
