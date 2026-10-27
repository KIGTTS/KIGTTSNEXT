package com.kigtts.next.ui.identity

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.kigtts.next.cards.StickerRepository
import com.kigtts.next.identity.*
import com.kigtts.next.sharing.LocalFaceShare
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiTethering

/** Reused by the card page, personal-information preview and the live background editor. */
@Composable internal fun PersonalCardOverlay(identity: IdentityCard, stickers: StickerRepository,
    modifier: Modifier = Modifier, interactive: Boolean = true, backgroundColor: Long = 0xFF245E63,
    hasImage: Boolean = true, backgroundPixels: android.graphics.Bitmap? = null, backgroundInk: Int? = null) {
    var contact by remember { mutableStateOf<ContactLink?>(null) }
    val faceShare = LocalFaceShare.current
    val sampledInk = remember(backgroundPixels) { backgroundPixels?.let(::identityInk) }
    val ink = (backgroundInk ?: sampledInk)?.let(::Color) ?:
        if (!hasImage && Color(if (identity.gradient) identity.gradientColor else backgroundColor).luminance() > .42f) Color(0xFF202020) else Color.White
    val shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = .5f), androidx.compose.ui.geometry.Offset(0f, 1.5f), 4f)
    BoxWithConstraints(modifier) {
        val compact = maxWidth < 300.dp
        val padding = if (compact) 12.dp else 20.dp
        CompositionLocalProvider(LocalContentColor provides ink) {
            PersonalContactButtons(identity.contacts, interactive, { contact = it },
                Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 8.dp, end = 56.dp).fillMaxWidth())
            if (interactive && faceShare != null) PersonalCardActionButton("开启面对面", "personal-face-share", true,
                { faceShare.send(listOf(identity.designId)) }, Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(Icons.Default.WifiTethering, "开启面对面", tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Row(Modifier.align(Alignment.BottomStart).padding(padding), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PersonalAvatar(identity.avatar?.let(stickers::imageFile), Modifier.size(if (compact) 42.dp else 54.dp), identity.avatarFrame)
                Column(Modifier.weight(1f)) {
                    Text(identity.nickname.ifBlank { "我的名片" }, color = ink, style = MaterialTheme.typography.titleLarge.copy(shadow = shadow), maxLines = 1)
                    if (identity.bio.isNotBlank()) Text(identity.bio, color = ink.copy(alpha = .86f), style = MaterialTheme.typography.bodyMedium.copy(shadow = shadow), maxLines = 2)
                }
            }
        }
    }
    contact?.let { PersonalContactDialog(it) { contact = null } }
}
