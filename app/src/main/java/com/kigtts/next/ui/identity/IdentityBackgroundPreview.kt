package com.kigtts.next.ui.identity

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.kigtts.next.cards.StickerRepository
import com.kigtts.next.identity.*
import com.kigtts.plugin.api.PolaroidCard
import kotlinx.coroutines.*

@Composable internal fun rememberIdentityBackground(card: PolaroidCard, identity: IdentityCard, stickers: StickerRepository, height: Int): Bitmap? {
    val cache = stickers.identityBackgrounds
    val key = cache.key(card, identity)
    var pixels by remember(card.id) { mutableStateOf(cache.preview(card, identity, height)) }
    LaunchedEffect(key, cache.height(height)) {
        try { pixels = cache.load(card, identity, height) }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { /* The live photograph remains available if a cache cannot be produced. */ }
    }
    return pixels
}

@Composable internal fun rememberIdentityPainter(card: PolaroidCard, identity: IdentityCard, stickers: StickerRepository): IdentityBackgroundPainter? {
    // Pose and foreground edits must not cancel a texture decode or rebuild the background artwork.
    val base = card.copy(photoX = .5, photoY = .5, photoZoom = 1.0, layers = emptyList(), title = "", description = "")
    val key = remember(base, identity.pattern, identity.gradient, identity.gradientColor, identity.cardFrameId) {
        stickers.identityBackgrounds.key(base, identity)
    }
    var painter by remember(card.imageId) { mutableStateOf<IdentityBackgroundPainter?>(null) }
    LaunchedEffect(key) {
        try { painter = stickers.identityBackgrounds.prepare(base, identity) }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { }
    }
    return painter
}

@Composable internal fun IdentityLiveBackground(card: PolaroidCard, painter: IdentityBackgroundPainter?) {
    Canvas(Modifier.fillMaxSize().background(Color(card.frameColor))) {
        if (painter != null) drawIntoCanvas { painter.draw(it.nativeCanvas, size.width, size.height, card) }
    }
}

@Composable internal fun IdentityBackgroundPreview(pixels: Bitmap?) {
    if (pixels != null) Image(pixels.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
}
