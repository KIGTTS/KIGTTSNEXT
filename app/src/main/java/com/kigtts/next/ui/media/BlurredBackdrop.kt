package com.kigtts.next.ui.media

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.kigtts.next.media.GaussianBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun BlurredBackdrop(source: Bitmap, modifier: Modifier = Modifier) {
    val image by produceState<ImageBitmap?>(null, source) {
        value = withContext(Dispatchers.Default) { runCatching { GaussianBackdrop.blur(source).asImageBitmap() }.getOrNull() }
    }
    image?.let { Image(it, null, modifier.clipToBounds(), contentScale = ContentScale.Crop) }
}
