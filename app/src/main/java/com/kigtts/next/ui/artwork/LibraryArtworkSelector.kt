package com.kigtts.next.ui.artwork

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.cards.rememberCardImage
import com.kigtts.next.ui.sketch.SketchAlbumRepository
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import java.io.File

/** All three libraries keep their own identities, groups and source records. */
@Composable internal fun LibraryArtworkSelector(model: AppViewModel, request: ArtworkSelectionRequest,
    close: () -> Unit, confirm: (List<String>) -> Unit) {
    val photos by model.cards.cards.collectAsState()
    val albums by model.cards.albums.collectAsState()
    val identities by model.identities.items.collectAsState()
    val designs by model.stickers.identityDesigns.items.collectAsState()
    val sketches = remember(model) { SketchAlbumRepository(model.getApplication()) }
    var works by remember { mutableStateOf(emptyList<com.kigtts.next.ui.sketch.SketchArtwork>()) }
    LaunchedEffect(request.library) { if (request.library == ArtworkLibrary.SKETCHES) works = withContext(Dispatchers.IO) { sketches.list() } }
    val entries = when (request.library) {
        ArtworkLibrary.POLAROIDS -> photos.filter { it.frameStyleId != "identity.banner" }.map { card ->
            ArtworkChoice(card.id, card.title.ifBlank { "照片" }, albums.firstOrNull { card.id in it.cardIds }?.name.orEmpty()) }
        ArtworkLibrary.PERSONAL_CARDS -> identities.filter { identity -> designs.any { it.id == identity.designId } }.map { ArtworkChoice(it.id, it.nickname.ifBlank { "名片" }) }
        ArtworkLibrary.SKETCHES -> works.map { ArtworkChoice(it.id, "画板作品") }
    }
    ArtworkSelectorDialog(request.title, entries, request.multiple, request.selectedIds, close, confirm) { item, modifier, preview ->
        val revision = when (request.library) {
            ArtworkLibrary.POLAROIDS -> photos.firstOrNull { it.id == item.id }.hashCode()
            ArtworkLibrary.PERSONAL_CARDS -> identities.firstOrNull { it.id == item.id }.hashCode() + designs.hashCode()
            else -> 0
        }
        val file by produceState<File?>(null, item.id, revision) {
            value = withContext(Dispatchers.IO) { runCatching { com.kigtts.next.artwork.artworkImage(model, request.library, item.id) }.getOrNull() }
        }
        val image = rememberCardImage(file, if (preview) 1200 else 384)
        Box(modifier, contentAlignment = Alignment.Center) {
            if (image != null) Image(image, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}
