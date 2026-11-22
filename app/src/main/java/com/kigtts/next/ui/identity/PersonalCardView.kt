package com.kigtts.next.ui.identity

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kigtts.next.cards.StickerRepository
import com.kigtts.next.identity.IdentityCard
import com.kigtts.next.ui.cards.PolaroidCanvas
import com.kigtts.next.ui.cards.rememberCardImage
import com.kigtts.next.ui.components.feedbackClick

@Composable
internal fun PersonalCardView(identity: IdentityCard, stickers: StickerRepository, modifier: Modifier = Modifier, interactive: Boolean = true,
    editing: Boolean = false, editInformation: () -> Unit = {}, editBackground: () -> Unit = {}, onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(24.dp), onOpen: (() -> Unit)? = null) {
    val designs by stickers.identityDesigns.items.collectAsState()
    val card = designs.firstOrNull { it.id == identity.designId }
    val interaction = when {
        editing -> Modifier.clickable(onClickLabel = "编辑名片背景", onClick = feedbackClick(editBackground))
        onLongClick != null -> Modifier.combinedClickable(onClick = onOpen?.let { feedbackClick(it) } ?: {},
            onLongClick = feedbackClick(onLongClick))
        onOpen != null -> Modifier.clickable(onClick = feedbackClick(onOpen))
        else -> Modifier
    }
    Surface(modifier.aspectRatio(16f / 9f).then(interaction),
        shape = shape, color = MaterialTheme.colorScheme.primaryContainer) {
        BoxWithConstraints {
            val height = with(LocalDensity.current) { maxHeight.roundToPx() }
            val pixels = card?.let { rememberIdentityBackground(it, identity, stickers, height) }
            val painter = if (card != null && pixels == null) rememberIdentityPainter(card, identity, stickers) else null
            card?.let { PolaroidCanvas(it, stickers::imageFile, Modifier.fillMaxSize(), frame = stickers.frameStyle(it), paperElevation = 0.dp,
                photoContent = { if (pixels != null) IdentityBackgroundPreview(pixels) else IdentityLiveBackground(it, painter) }) }
            PersonalCardOverlay(identity, stickers, Modifier.matchParentSize(), interactive,
                backgroundColor = card?.frameColor ?: 0xFF245E63, hasImage = card?.imageId != null, backgroundPixels = pixels,
                editing = editing, editInformation = editInformation, editBackground = editBackground)
        }
    }
}

@Composable
internal fun PersonalAvatar(file: java.io.File?, modifier: Modifier = Modifier, frame: String = "circle") {
    BoxWithConstraints(modifier.clip(rememberAvatarShape(frame)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        val pixels = with(LocalDensity.current) { maxWidth.roundToPx() }
        val image = rememberCardImage(file, (pixels + 31).coerceAtLeast(192) / 32 * 32)
        if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Default.Person, null, Modifier.fillMaxSize().padding(10.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
