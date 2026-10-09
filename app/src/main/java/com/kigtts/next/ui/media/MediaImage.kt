package com.kigtts.next.ui.media

import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.ImageDecoder
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.Alignment
import com.kigtts.next.media.MediaScale
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Decode off the UI thread and cap the decoded dimension, including HEIF orientation. */
@Composable
fun MediaImage(file: File?, description: String, modifier: Modifier = Modifier, maximum: Int = 2048,
    scale: MediaScale = MediaScale.FIT, backdrop: Boolean = false, contentModifier: Modifier = Modifier) {
    var loaded by remember(file?.path, file?.lastModified(), maximum) { mutableStateOf(false) }
    val bitmap by produceState<ImageBitmap?>(null, file?.path, file?.lastModified(), maximum) {
        value = withContext(Dispatchers.IO) { file?.let { runCatching { decodeImage(it, maximum) }.getOrNull() } }
        loaded = true
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            if (backdrop && scale == MediaScale.FIT) BlurredBackdrop(bitmap!!.asAndroidBitmap(), Modifier.fillMaxSize())
            Image(bitmap!!, description, Modifier.fillMaxSize().then(contentModifier), contentScale = if (scale == MediaScale.FIT) ContentScale.Fit else ContentScale.Crop)
        } else Text(if (file == null) "图片资源不可用" else if (loaded) "无法解码此图片" else "正在加载图片", style = MaterialTheme.typography.bodySmall)
    }
}

internal fun decodeImage(file: File, maximum: Int): ImageBitmap? {
    if (Build.VERSION.SDK_INT >= 28) runCatching { ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val size = info.size; val factor = maxOf(size.width, size.height).toFloat() / maximum
        if (factor > 1) decoder.setTargetSize((size.width / factor).toInt().coerceAtLeast(1), (size.height / factor).toInt().coerceAtLeast(1))
    }.asImageBitmap() }.getOrNull()?.let { return it }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1; while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maximum) sample *= 2
    val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val exif = runCatching { ExifInterface(file) }.getOrNull()
    val matrix = Matrix().apply { exif?.let { if (it.isFlipped) postScale(-1f, 1f); postRotate(it.rotationDegrees.toFloat()) } }
    return android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).asImageBitmap()
}
