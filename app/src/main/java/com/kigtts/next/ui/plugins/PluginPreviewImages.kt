package com.kigtts.next.ui.plugins

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.kigtts.next.media.UiIcons
import com.kigtts.plugin.api.PageImage
import com.kigtts.plugin.api.PluginResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private data class PreviewKey(val path: String, val modified: Long, val pixels: Int)
private val previewCache = object : LruCache<PreviewKey, ImageBitmap>(12 * 1024 * 1024) {
    override fun sizeOf(key: PreviewKey, value: ImageBitmap) = value.width * value.height * 4
}
private val previewDecoder = Dispatchers.IO.limitedParallelism(2)
internal data class PreviewArtwork(val bitmap: ImageBitmap? = null, val loading: Boolean = true)

@Composable
internal fun previewBitmap(image: PageImage, resolve: (PluginResource) -> File?, targetPixels: Int = 1200): PreviewArtwork {
    // Market artwork uses content-addressed paths. Other plugins may overwrite a preview at the same path.
    val localFile = if (LocalProgressiveMarketContent.current) null
        else remember(image.resource) { runCatching { resolve(image.resource) }.getOrNull() }
    val modified = localFile?.lastModified()
    return key(image.resource, targetPixels, modified) {
        produceState(PreviewArtwork(), image.resource, targetPixels, modified) {
            val bitmap = withContext(previewDecoder) {
                runCatching {
                    val file = (localFile ?: resolve(image.resource))?.takeIf { it.isFile && it.length() in 1..8L * 1024 * 1024 }
                        ?: return@withContext null
                    val cacheKey = PreviewKey(file.path, file.lastModified(), targetPixels)
                    previewCache.get(cacheKey) ?: decodePreview(file, targetPixels)?.asImageBitmap()?.also {
                        previewCache.put(cacheKey, it)
                    }
                }.getOrNull()
            }
            value = PreviewArtwork(bitmap, loading = false)
        }.value
    }
}

private fun decodePreview(file: File, targetPixels: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        if (file.length() > 512L * 1024) return null
        return UiIcons.renderPreviewSvg(file.readText(Charsets.UTF_8), targetPixels)
    }
    val sample = Integer.highestOneBit((maxOf(bounds.outWidth, bounds.outHeight) / targetPixels).coerceAtLeast(1))
    val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val size = maxOf(decoded.width, decoded.height)
    if (size <= targetPixels) return decoded
    val scale = targetPixels.toFloat() / size
    return Bitmap.createScaledBitmap(decoded, (decoded.width * scale).roundToInt().coerceAtLeast(1),
        (decoded.height * scale).roundToInt().coerceAtLeast(1), true).also { if (it !== decoded) decoded.recycle() }
}
