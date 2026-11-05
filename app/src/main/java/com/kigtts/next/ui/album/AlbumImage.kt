package com.kigtts.next.ui.album

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.ThumbnailUtils
import android.os.Build
import android.util.Size
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.kigtts.next.media.AlbumMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val thumbnails = object : android.util.LruCache<String, Bitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
}

@Composable internal fun AlbumImage(media: AlbumMedia, modifier: Modifier = Modifier, full: Boolean = false) {
    val context = LocalContext.current
    val key = "${media.uri}/${media.takenAt}/${if (full) 2048 else 320}"
    val bitmap by produceState<Bitmap?>(thumbnails.get(key), key) {
        value = withContext(Dispatchers.IO) {
            thumbnails.get(key) ?: runCatching {
                if (!full || media.video) {
                    if (Build.VERSION.SDK_INT >= 29) context.contentResolver.loadThumbnail(media.uri, Size(320, 320), null)
                    else @Suppress("DEPRECATION") if (media.video) android.provider.MediaStore.Video.Thumbnails.getThumbnail(
                        context.contentResolver, media.uri.lastPathSegment!!.toLong(), android.provider.MediaStore.Video.Thumbnails.MINI_KIND, null)
                    else android.provider.MediaStore.Images.Thumbnails.getThumbnail(context.contentResolver,
                        media.uri.lastPathSegment!!.toLong(), android.provider.MediaStore.Images.Thumbnails.MINI_KIND, null)
                } else if (Build.VERSION.SDK_INT >= 28) ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, media.uri)) { decoder, info, _ ->
                    val scale = minOf(1f, 2048f / maxOf(info.size.width, info.size.height))
                    decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                } else {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(media.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    var sample = 1; while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
                    context.contentResolver.openInputStream(media.uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
                }
            }.getOrNull()?.also { thumbnails.put(key, it) }
        }
    }
    val opacity by animateFloatAsState(if (bitmap == null) 0f else 1f, label = "album-image")
    Box(modifier.background(if (full) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh)) {
        bitmap?.let { Image(it.asImageBitmap(), media.name, Modifier.fillMaxSize().graphicsLayer { alpha = opacity },
            contentScale = if (full) ContentScale.Fit else ContentScale.Crop) }
    }
}
