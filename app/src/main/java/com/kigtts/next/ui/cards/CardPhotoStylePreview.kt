package com.kigtts.next.ui.cards

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.kigtts.next.cards.*
import com.kigtts.next.media.thumbnail.ThumbnailMemoryCache
import com.kigtts.next.ui.cards.camera.applyPhotoFilterBands
import com.kigtts.plugin.api.PolaroidPhotoStyle
import kotlinx.coroutines.*
import java.io.File

private val styledPhotos = ThumbnailMemoryCache(16 * 1024 * 1024)

/** Cancel superseded slider work; keep the last rendered pixels until the newest recipe is ready. */
@Composable internal fun rememberCardPhotoStyle(image: ImageBitmap?, owner: Any, style: PolaroidPhotoStyle?,
    imageFile: (String) -> File?, captured: CapturedPhotoStyle? = null): ImageBitmap? {
    val active = captured?.let { it.effects.active || it.filter != com.kigtts.next.ui.cards.camera.PhotoFilter.ORIGINAL && it.strength > 0f }
        ?: (style?.hasPhotoTreatment() == true)
    var rendered by remember(owner) { mutableStateOf<ImageBitmap?>(null) }
    val files = rememberUpdatedState(imageFile)
    LaunchedEffect(image, style, captured, active) {
        if (!active) { rendered = image; return@LaunchedEffect }
        val source = image?.asAndroidBitmap() ?: return@LaunchedEffect
        suspend fun render(maximum: Int): ImageBitmap {
            val edge = minOf(maximum, maxOf(source.width, source.height))
            val key = "${source.generationId}|${style ?: captured}|$edge"
            return (styledPhotos[key] ?: withContext(Dispatchers.Default) {
                val scale = minOf(1f, edge.toFloat() / maxOf(source.width, source.height))
                val resized = Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1),
                    (source.height * scale).toInt().coerceAtLeast(1), true)
                val target = resized.copy(Bitmap.Config.ARGB_8888, true)
                if (resized !== source) resized.recycle()
                try {
                    if (captured != null) applyPhotoFilterBands(target, captured.filter, captured.strength, captured.lut, captured.effects)
                    else applyCardPhotoStyle(target, style, files.value)
                    currentCoroutineContext().ensureActive()
                    styledPhotos.put(key, target)
                    target
                } catch (failure: Throwable) { target.recycle(); throw failure }
            }).asImageBitmap()
        }
        try {
            rendered = render(512)
            if (maxOf(source.width, source.height) > 512) { delay(160); rendered = render(1200) }
        } catch (cancel: CancellationException) { throw cancel }
        catch (failure: Exception) { android.util.Log.w("KigPhotoStyle", "Preview treatment unavailable", failure) }
    }
    return if (active) rendered else image
}
