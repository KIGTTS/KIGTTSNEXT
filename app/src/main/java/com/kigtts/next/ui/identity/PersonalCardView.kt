package com.kigtts.next.ui.identity

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.kigtts.next.cards.StickerRepository
import com.kigtts.next.identity.IdentityCard
import com.kigtts.next.ui.cards.PolaroidCanvas
import com.kigtts.next.ui.cards.rememberCardImage

@Composable
internal fun PersonalCardView(identity: IdentityCard, stickers: StickerRepository, modifier: Modifier = Modifier, interactive: Boolean = true) {
    val designs by stickers.identityDesigns.items.collectAsState()
    val card = designs.firstOrNull { it.id == identity.designId }
    val pixels = rememberIdentityBackground(card, identity, stickers)
    Surface(modifier.aspectRatio(16f / 9f), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Box {
            card?.let { PolaroidCanvas(it, stickers::imageFile, Modifier.fillMaxSize(), frame = stickers.frameStyle(it), paperElevation = 0.dp, backgroundDecoration = { IdentityBackgroundPreview(pixels) }) }
            PersonalCardOverlay(identity, stickers, Modifier.matchParentSize(), interactive,
                backgroundColor = card?.frameColor ?: 0xFF245E63, hasImage = card?.imageId != null, backgroundPixels = pixels)
        }
    }
}

@Composable
internal fun PersonalAvatar(file: java.io.File?, modifier: Modifier = Modifier, frame: String = "circle") {
    val image = rememberCardImage(file, 192)
    Box(modifier.clip(com.kigtts.next.identity.AvatarFrames.shape(frame)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Default.Person, null, Modifier.fillMaxSize().padding(10.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
