package com.kigtts.next.ui.sketch

import com.kigtts.next.i18n.rememberUiCopy

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun SketchArtworkImage(repository: SketchAlbumRepository, id: String, modifier: Modifier = Modifier) {
    val copy = rememberUiCopy()
    val image by produceState<ImageBitmap?>(null, repository, id) {
        value = withContext(Dispatchers.IO) { runCatching {
            val file = repository.imageFile(id)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 960) inSampleSize *= 2
            }
            BitmapFactory.decodeFile(file.absolutePath, options)?.asImageBitmap()
        }.getOrNull() }
    }
    val bitmap = image
    if (bitmap != null) Image(bitmap, copy("sketch.artwork"), modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    else Icon(Icons.Default.Image, copy("sketch.loading_artwork"), modifier)
}
