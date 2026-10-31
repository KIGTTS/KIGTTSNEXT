package com.kigtts.next.ui.cards

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import com.kigtts.next.ui.media.BlurredBackdrop
import com.kigtts.plugin.api.PhotoBackdropMode
import com.kigtts.plugin.api.PolaroidCard

private class PhotoPixelsMemory(var image: ImageBitmap?, var style: com.kigtts.plugin.api.PolaroidPhotoStyle?)
private class PhotoBlend(val previous: ImageBitmap?, val current: ImageBitmap?) {
    val opacity = Animatable(if (previous === current) 1f else 0f)
    var complete by mutableStateOf(previous === current)
}

/** Loading and LOD changes retain the previous pixels until the new image has faded in. */
@Composable
internal fun CardPhotoContent(card: PolaroidCard, image: ImageBitmap?, width: Float, height: Float) {
    key(card.id, card.imageId) {
        val memory = remember { PhotoPixelsMemory(image, card.photoStyle) }
        // Create both layers and their initial alpha in the same composition. Resetting
        // a live layer from an effect can expose one empty frame before its underlay exists.
        val blend = remember(image) {
            PhotoBlend(if (memory.image != null && memory.style != card.photoStyle) image else memory.image, image)
                .also { memory.image = image; memory.style = card.photoStyle }
        }
        LaunchedEffect(blend) {
            blend.opacity.animateTo(1f, tween(180))
            blend.complete = true
        }
        // Leave the outgoing image opaque underneath. Fading both layers would expose
        // the gray paper between two identical LODs and cause a brightness flash.
        Box(Modifier.fillMaxSize()) {
            if (!blend.complete) PhotoPixels(card, blend.previous, width, height)
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = blend.opacity.value }) {
                PhotoPixels(card, blend.current, width, height)
            }
        }
    }
}

@Composable
private fun PhotoPixels(card: PolaroidCard, pixels: ImageBitmap?, width: Float, height: Float) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (pixels == null) {
            Icon(Icons.Default.Image, "拍立得照片", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            if (card.photoBackdrop == PhotoBackdropMode.BLUR)
                BlurredBackdrop(pixels.asAndroidBitmap(), Modifier.fillMaxSize())
            Image(pixels, card.title, Modifier.fillMaxSize().graphicsLayer {
                scaleX = card.photoZoom.toFloat(); scaleY = card.photoZoom.toFloat()
                translationX = ((card.photoX - .5) * width).toFloat()
                translationY = ((card.photoY - .5) * height).toFloat()
            }, contentScale = ContentScale.Fit)
        }
    }
}
