package com.kigtts.next.ui.cards.camera

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val THUMBNAIL_EDGE = 128
private val thumbnailWorker = Dispatchers.Default.limitedParallelism(1)
private val lookThumbs = LruCache<String, ImageBitmap>(40)
private var referencePixels: IntArray? = null // Accessed only on the thumbnail worker.

/** Lazy rows request thumbnails as their tiles appear; reopening reuses finished tiles. */
@Composable
internal fun rememberCameraLookThumbnail(key: String, render: suspend (Bitmap) -> Unit): ImageBitmap? {
    val assets = LocalContext.current.assets
    return produceState(lookThumbs.get(key), assets, key) {
        value = withContext(thumbnailWorker) {
            lookThumbs.get(key) ?: cameraReferencePixels(assets)?.let { pixels ->
                val bitmap = Bitmap.createBitmap(THUMBNAIL_EDGE, THUMBNAIL_EDGE, Bitmap.Config.ARGB_8888)
                var cached = false
                try {
                    bitmap.setPixels(pixels, 0, THUMBNAIL_EDGE, 0, 0, THUMBNAIL_EDGE, THUMBNAIL_EDGE)
                    render(bitmap)
                    bitmap.asImageBitmap().also {
                        lookThumbs.put(key, it)
                        cached = true
                    }
                } catch (_: IllegalArgumentException) {
                    null
                } finally {
                    if (!cached) bitmap.recycle()
                }
            }
        }
    }.value
}

private fun cameraReferencePixels(assets: AssetManager): IntArray? {
    referencePixels?.let { return it }
    val source = decodeCameraReferencePhoto(assets, 2) ?: return null
    val square = Bitmap.createBitmap(THUMBNAIL_EDGE, THUMBNAIL_EDGE, Bitmap.Config.ARGB_8888)
    try {
        val side = minOf(source.width, source.height)
        val left = (source.width - side) / 2
        val top = (source.height - side) / 2
        Canvas(square).drawBitmap(source, Rect(left, top, left + side, top + side),
            Rect(0, 0, THUMBNAIL_EDGE, THUMBNAIL_EDGE), Paint(Paint.FILTER_BITMAP_FLAG))
        return IntArray(THUMBNAIL_EDGE * THUMBNAIL_EDGE).also {
            square.getPixels(it, 0, THUMBNAIL_EDGE, 0, 0, THUMBNAIL_EDGE, THUMBNAIL_EDGE)
            referencePixels = it
        }
    } finally {
        source.recycle()
        square.recycle()
    }
}
