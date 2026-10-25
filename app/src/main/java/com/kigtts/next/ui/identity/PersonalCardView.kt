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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.kigtts.next.cards.StickerRepository
import com.kigtts.next.identity.IdentityCard
import com.kigtts.next.ui.cards.PolaroidCanvas
import com.kigtts.next.ui.cards.rememberCardImage

@Composable
internal fun PersonalCardView(identity: IdentityCard, stickers: StickerRepository, modifier: Modifier = Modifier) {
    val snapshot by stickers.snapshot.collectAsState()
    val card = snapshot.cards.firstOrNull { it.id == identity.designId }
    Surface(modifier.aspectRatio(16f / 9f), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Box {
            card?.let { PolaroidCanvas(it, stickers::imageFile, Modifier.fillMaxSize(),
                frame = stickers.frameStyle(it), paperElevation = 0.dp) }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .78f)))))
            Row(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PersonalAvatar(identity.avatar?.let(stickers::imageFile), Modifier.size(54.dp))
                Column {
                    Text(identity.nickname.ifBlank { "我的名片" }, color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                    if (identity.bio.isNotBlank()) Text(identity.bio, color = Color.White.copy(alpha = .86f),
                        style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                }
            }
        }
    }
}

@Composable
internal fun PersonalAvatar(file: java.io.File?, modifier: Modifier = Modifier) {
    val image = rememberCardImage(file, 192)
    Box(modifier.clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Default.Person, null, Modifier.fillMaxSize().padding(10.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
