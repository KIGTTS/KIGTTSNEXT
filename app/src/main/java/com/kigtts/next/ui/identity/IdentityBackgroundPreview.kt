package com.kigtts.next.ui.identity

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.kigtts.next.cards.StickerRepository
import com.kigtts.next.identity.*
import com.kigtts.plugin.api.PolaroidCard
import kotlinx.coroutines.*

@Composable internal fun rememberIdentityBackground(card: PolaroidCard?, identity: IdentityCard, stickers: StickerRepository): Bitmap? {
    var pixels by remember(card?.id) { mutableStateOf<Bitmap?>(null) }
    val background = card?.copy(layers = emptyList(), title = "", description = "")
    LaunchedEffect(background, identity.pattern, identity.gradient, identity.gradientColor, identity.cardFrameId) {
        if (background != null) pixels = withContext(Dispatchers.Default) { renderIdentityBackground(background, identity, stickers) }
    }
    return pixels
}

@Composable internal fun IdentityBackgroundPreview(pixels: Bitmap?) {
    if (pixels != null) Image(pixels.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
}
