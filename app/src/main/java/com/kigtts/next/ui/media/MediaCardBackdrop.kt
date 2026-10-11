package com.kigtts.next.ui.media

import androidx.compose.animation.core.tween
import androidx.compose.animation.*
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
import androidx.compose.ui.semantics.clearAndSetSemantics

/** Shared by list items, media library and drag ghosts. */
@Composable
fun MediaCardBackdrop(entry: MediaEntry?, repository: MediaRepository, modifier: Modifier = Modifier,
    surface: Color = MaterialTheme.colorScheme.surfaceContainerLowest) {
    if (entry == null) return
    val revision by repository.thumbnails.revision.collectAsStateWithLifecycle()
    var size by remember { mutableStateOf(0 to 0) }
    var bitmap by remember(entry.id) { mutableStateOf(repository.thumbnails.latest(entry)) }
    // Cached previews appear immediately in a new drag/transition node; avoid a blank first frame.
    if (size.first > 0) repository.thumbnails.cached(entry, size.first, size.second)?.let {
        if (bitmap == null) bitmap = it
    }
    LaunchedEffect(entry, size, revision) {
        if (size.first > 0) try { repository.thumbnails.thumbnail(entry, size.first, size.second)?.let { bitmap = it } }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { /* Keep a prepared preview while a newer version is unavailable. */ }
    }
    val scrim = remember(surface) {
        val maximum = (if (surface.luminance() > .5f) .58f else .66f) * surface.alpha
        (0..24).map { i ->
            val position = i / 24f
            val t = ((position - .48f) / .52f).coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
            position to surface.copy(alpha = maximum * (1 - t) + .035f * surface.alpha * t)
        }.toTypedArray()
    }
    Box(modifier.onSizeChanged { size = MediaThumbnails.size(it.width, it.height) }) {
        AnimatedContent(bitmap,Modifier.fillMaxSize(),transitionSpec={
            // Keep the old image fully drawn under the new one, preventing a dip to the card fill.
            fadeIn(tween(220)).togetherWith(fadeOut(tween(1,delayMillis=220))).using(null)
        },label="thumbnail-version") { frame ->
        val frameModifier=Modifier.fillMaxSize().then(if(frame!==bitmap)Modifier.clearAndSetSemantics {} else Modifier)
        val current=remember(frame) { frame?.asImageBitmap() }
        Box(frameModifier) {
        if (current != null) Canvas(Modifier.fillMaxSize().testTag("media-thumbnail-" + entry.id)) {
            drawImage(current, dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt()))
            drawRect(Brush.horizontalGradient(colorStops = scrim))
        } else Icon(when (entry.kind) {
            MediaKind.VIDEO -> Icons.Default.Movie
            MediaKind.MOTION_PHOTO -> Icons.Default.MotionPhotosOn
            MediaKind.HTML, MediaKind.WEB -> Icons.Default.Language
            MediaKind.AUDIO -> Icons.Default.AudioFile
            else -> Icons.Default.Image
        }, null, Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).size(28.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = .45f))
        }
        }
    }
}

@Composable
internal fun PhraseMediaBackdrop(id: String?, repository: MediaRepository?, modifier: Modifier, surface: Color = MaterialTheme.colorScheme.surfaceContainerLowest) {
    if (id == null || repository == null) return
    val entries by repository.entries.collectAsStateWithLifecycle()
    MediaCardBackdrop(entries.firstOrNull { it.id == id }, repository, modifier, surface)
}
