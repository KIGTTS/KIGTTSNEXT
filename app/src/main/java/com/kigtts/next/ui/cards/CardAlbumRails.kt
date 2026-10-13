@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
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
    temporarilyLocked: Boolean = false) {
    val albumItem: @Composable (PolaroidAlbum) -> Unit = { album ->
        val press = rememberImmediateButtonPress(remember { androidx.compose.foundation.interaction.MutableInteractionSource() })
        LaunchedEffect(enabled) { if (!enabled) press.cancel() }
        FilledTonalButton(onClick = feedbackClick { press.click(); chooseAlbum(album.id) },
            modifier = Modifier.then(if (landscape) Modifier.fillMaxWidth() else Modifier)
                .testTag("card-album/" + album.id).semantics { selected = album.id == selectedId }
                .then(if (enabled) Modifier.immediateButtonPress(press) else Modifier),
            enabled = enabled, shapes = ButtonDefaults.shapes(), interactionSource = press.native,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = if (album.id == selectedId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (album.id == selectedId) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                .let { if (temporarilyLocked) it.keepColorsWhenLocked() else it },
            contentPadding = PaddingValues(horizontal = if (landscape) 8.dp else 12.dp, vertical = 8.dp)) {
            Icon(Icons.Default.PhotoAlbum, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text(album.name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
    val photoItem: @Composable (PolaroidCard) -> Unit = { card ->
        val image = card.imageId?.let { rememberCardImage(imageFile(it), 256) }
        Surface(Modifier.size(64.dp, 82.dp).testTag("card-album-photo/" + card.id)
            .clickable(enabled = enabled, onClick = feedbackClick { choosePhoto(card) })
            .semantics { contentDescription = card.title.ifBlank { "拍立得" }; selected = card.id == focusedId },
            color = Color.White, shape = RoundedCornerShape(4.dp),
            border = if (card.id == focusedId) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
            Box(Modifier.padding(start = 5.dp, end = 5.dp, top = 5.dp, bottom = 16.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Default.Image, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    val orientation = if (landscape) "竖向滚动" else "横向滚动"
    if (landscape) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        LazyColumn(Modifier.weight(1f).fillMaxHeight().testTag("card-albums").semantics { stateDescription = orientation },
            state = albumScroll, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(albums, key = { it.id }) { albumItem(it) }
        }
        if (photos.isEmpty()) Box(Modifier.width(76.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Text("还没有照片", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else LazyColumn(Modifier.width(76.dp).fillMaxHeight().testTag("card-album-photos").semantics { stateDescription = orientation },
            state = photoScroll, verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(4.dp)) {
            items(photos, key = { it.id }) { photoItem(it) }
        }
    } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LazyRow(Modifier.fillMaxWidth().height(48.dp).testTag("card-albums").semantics { stateDescription = orientation },
            state = albumScroll, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(albums, key = { it.id }) { albumItem(it) }
        }
        if (photos.isEmpty()) Text("这个相册还没有照片", Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else LazyRow(Modifier.fillMaxWidth().height(92.dp).testTag("card-album-photos").semantics { stateDescription = orientation },
            state = photoScroll, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(4.dp)) {
            items(photos, key = { it.id }) { photoItem(it) }
        }
    }
}
