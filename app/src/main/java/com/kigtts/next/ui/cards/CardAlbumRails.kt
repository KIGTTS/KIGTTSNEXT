@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.*
import java.io.File

@Composable
internal fun CardAlbumRails(albums: List<PolaroidAlbum>, selectedId: String, photos: List<PolaroidCard>,
    focusedId: String?, imageFile: (String) -> File?, enabled: Boolean, landscape: Boolean,
    albumScroll: LazyListState, photoScroll: LazyListState, chooseAlbum: (String) -> Unit, choosePhoto: (PolaroidCard) -> Unit,
    temporarilyLocked: Boolean = false, manageAlbum: (String) -> Unit = {}) {
    val sources = albums.map { album -> key(album.id) { remember { MutableInteractionSource() } } }
    val pressed = sources.map { it.collectIsPressedAsState().value }
    val albumItem: @Composable (Int, PolaroidAlbum) -> Unit = { index, album ->
        val selected = album.id == selectedId
        val press = rememberImmediateButtonPress(sources[index])
        LaunchedEffect(enabled) { if (!enabled) press.cancel() }
        val neighbors = if (landscape) CornerNeighbors(above = index > 0, below = index < albums.lastIndex)
            else CornerNeighbors(start = index > 0, end = index < albums.lastIndex)
        val shape = connectedShape(neighbors, neighborPressed = pressed.getOrElse(index - 1) { false } ||
            pressed.getOrElse(index + 1) { false }, outer = LocalCornerRadius.current.coerceIn(4.dp, 20.dp))
        val tonal = compactTonalButtonColors()
        val container by androidx.compose.animation.animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else tonal.containerColor,
            MaterialTheme.motionScheme.fastEffectsSpec(), label = "card-album-container")
        val content by androidx.compose.animation.animateColorAsState(if (selected) MaterialTheme.colorScheme.onPrimary else tonal.contentColor,
            MaterialTheme.motionScheme.fastEffectsSpec(), label = "card-album-content")
        FilledTonalButton(onClick = feedbackClick { press.click(); chooseAlbum(album.id) },
            modifier = Modifier.then(if (landscape) Modifier.fillMaxWidth() else Modifier)
                .then(if (landscape) Modifier.height(44.dp) else Modifier)
                .testTag("card-album/" + album.id).semantics { this.selected = album.id == selectedId }
                .then(if (enabled) Modifier.immediateButtonPress(press) else Modifier)
                .cardManagementLongPress(album.id, enabled, feedbackClick { manageAlbum(album.id) }),
            enabled = enabled, shapes = ButtonDefaults.shapes(if (selected) RoundedCornerShape(100.dp) else shape,
                RoundedCornerShape(8.dp)), interactionSource = press.native,
            colors = tonal.copy(containerColor = container, contentColor = content)
                .let { if (temporarilyLocked) it.keepColorsWhenLocked() else it }.animateEnabled(enabled),
            contentPadding = PaddingValues(horizontal = if (landscape) 6.dp else 12.dp, vertical = if (landscape) 6.dp else 8.dp)) {
            Icon(Icons.Default.PhotoAlbum, null, Modifier.size(if (landscape) 16.dp else 18.dp)); Spacer(Modifier.width(if (landscape) 4.dp else 6.dp))
            Text(album.name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = if (landscape) MaterialTheme.typography.labelMedium else LocalTextStyle.current)
        }
    }
    val photoItem: @Composable (PolaroidCard) -> Unit = { card ->
        val image = card.imageId?.let { rememberCardImage(imageFile(it), 256) }
        val geometry = polaroidFormat(card, null).geometry
        Surface(Modifier.then(if (landscape) Modifier.width(60.dp).aspectRatio(geometry.aspectRatio.toFloat()) else Modifier.size(64.dp, 82.dp))
            .testTag("card-album-photo/" + card.id)
            .clickable(enabled = enabled, onClick = feedbackClick { choosePhoto(card) })
            .semantics { contentDescription = card.title.ifBlank { "拍立得" }; selected = card.id == focusedId },
            color = Color.White, shape = RoundedCornerShape(4.dp),
            border = if (card.id == focusedId) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val photoBounds = if (landscape) Modifier.offset(x = maxWidth * (geometry.photoLeft / geometry.paperWidth).toFloat(),
                    y = maxWidth * (geometry.photoTop / geometry.paperWidth).toFloat())
                    .width(maxWidth * (geometry.photoWidth / geometry.paperWidth).toFloat())
                    .height(maxWidth * (geometry.photoHeight / geometry.paperWidth).toFloat())
                    else Modifier.fillMaxSize().padding(start = 5.dp, end = 5.dp, top = 5.dp, bottom = 16.dp)
                Box(photoBounds.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                    if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(Icons.Default.Image, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    val orientation = if (landscape) "竖向滚动" else "横向滚动"
    if (landscape) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LazyColumn(Modifier.weight(1f).fillMaxHeight().testTag("card-albums").semantics { stateDescription = orientation },
            state = albumScroll, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(albums, key = { _, album -> album.id }) { index, album -> albumItem(index, album) }
        }
        if (photos.isEmpty()) Box(Modifier.width(68.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Text("还没有照片", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        } else LazyColumn(Modifier.width(68.dp).fillMaxHeight().testTag("card-album-photos").semantics { stateDescription = orientation },
            state = photoScroll, verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(4.dp)) {
            items(photos, key = { it.id }) { photoItem(it) }
        }
    } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LazyRow(Modifier.fillMaxWidth().height(48.dp).testTag("card-albums").semantics { stateDescription = orientation },
            state = albumScroll, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(albums, key = { _, album -> album.id }) { index, album -> albumItem(index, album) }
        }
        if (photos.isEmpty()) Text("这个相册还没有照片", Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else LazyRow(Modifier.fillMaxWidth().height(92.dp).testTag("card-album-photos").semantics { stateDescription = orientation },
            state = photoScroll, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(4.dp)) {
            items(photos, key = { it.id }) { photoItem(it) }
        }
    }
}
