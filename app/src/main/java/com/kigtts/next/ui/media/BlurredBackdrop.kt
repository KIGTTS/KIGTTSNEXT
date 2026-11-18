package com.kigtts.next.ui.media

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.ContentScale
import com.kigtts.next.media.GaussianBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun BlurredBackdrop(source: Bitmap, modifier: Modifier = Modifier, showSourceWhileLoading: Boolean = false) {
    if(Build.VERSION.SDK_INT>=31) {
        val radius=with(LocalDensity.current) { 28.dp.toPx() }
        val effect=remember(radius) { android.graphics.RenderEffect.createBlurEffect(radius,radius,android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect() }
        Image(source.asImageBitmap(),null,modifier.clipToBounds().graphicsLayer { renderEffect=effect },contentScale=ContentScale.Crop)
        return
    }
    val image by produceState<ImageBitmap?>(null, source) {
        value = withContext(Dispatchers.Default) { runCatching { GaussianBackdrop.blur(source).asImageBitmap() }.getOrNull() }
    }
    val drawable = image ?: source.takeIf { showSourceWhileLoading }?.asImageBitmap()
    drawable?.let { Image(it, null, modifier.clipToBounds(), contentScale = ContentScale.Crop) }
}
