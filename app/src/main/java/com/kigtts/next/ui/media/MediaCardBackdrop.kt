package com.kigtts.next.ui.media

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.media.*
import com.kigtts.next.media.thumbnail.MediaThumbnails
import kotlinx.coroutines.CancellationException

/** Shared by list items, drag ghosts and container-transition snapshots. */
@Composable
fun MediaCardBackdrop(entry: MediaEntry?, repository: MediaRepository, modifier: Modifier = Modifier,
    surface: Color = MaterialTheme.colorScheme.surfaceContainerLowest) {
    if (entry == null) return
    val revision by repository.thumbnails.revision.collectAsStateWithLifecycle()
    var size by remember { mutableStateOf(0 to 0) }
    var bitmap by remember(entry.id) { mutableStateOf(repository.thumbnails.latest(entry)?.asImageBitmap()) }
    // Cached previews appear immediately in a new drag/transition node; avoid a blank first frame.
    if (size.first > 0) repository.thumbnails.cached(entry, size.first, size.second)?.let {
        if (bitmap == null) bitmap = it.asImageBitmap()
    }
    LaunchedEffect(entry, size, revision) {
        if (size.first > 0) try { bitmap = repository.thumbnails.thumbnail(entry, size.first, size.second)?.asImageBitmap() }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { bitmap = null }
    }
    val opacity by animateFloatAsState(if (bitmap != null) 1f else 0f, tween(180), label = "card-preview-reveal")
    val scrim = remember(surface) {
        val maximum = (if (surface.luminance() > .5f) .58f else .66f) * surface.alpha
        (0..24).map { i ->
            val position = i / 24f
            val t = ((position - .48f) / .52f).coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
            position to surface.copy(alpha = maximum * (1 - t) + .035f * surface.alpha * t)
        }.toTypedArray()
    }
    Box(modifier.onSizeChanged { size = MediaThumbnails.size(it.width, it.height) }) {
        val current = bitmap
        if (current != null) Canvas(Modifier.fillMaxSize().testTag("media-thumbnail-" + entry.id)) {
            drawImage(current, dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt()), alpha = opacity)
            drawRect(Brush.horizontalGradient(colorStops = scrim), alpha = opacity)
        } else Icon(when (entry.kind) {
            MediaKind.VIDEO -> Icons.Default.Movie
            MediaKind.MOTION_PHOTO -> Icons.Default.MotionPhotosOn
            MediaKind.HTML, MediaKind.WEB -> Icons.Default.Language
            MediaKind.AUDIO -> Icons.Default.AudioFile
            else -> Icons.Default.Image
        }, null, Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).size(28.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = .45f))
    }
}

@Composable
internal fun PhraseMediaBackdrop(id: String?, repository: MediaRepository?, modifier: Modifier, surface: Color = MaterialTheme.colorScheme.surfaceContainerLowest) {
    if (id == null || repository == null) return
    val entries by repository.entries.collectAsStateWithLifecycle()
    MediaCardBackdrop(entries.firstOrNull { it.id == id }, repository, modifier, surface)
}
