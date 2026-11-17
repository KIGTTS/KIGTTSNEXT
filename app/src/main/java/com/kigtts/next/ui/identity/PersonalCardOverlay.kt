package com.kigtts.next.ui.identity

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.kigtts.next.ui.components.feedbackClick

/** Reused by the card page, personal-information preview and the live background editor. */
@Composable internal fun PersonalCardOverlay(identity: IdentityCard, stickers: StickerRepository,
    modifier: Modifier = Modifier, interactive: Boolean = true, backgroundColor: Long = 0xFF245E63,
    hasImage: Boolean = true, backgroundPixels: android.graphics.Bitmap? = null, backgroundInk: Int? = null,
    editing: Boolean = false, editInformation: () -> Unit = {}, editBackground: () -> Unit = {}) {
    var contact by remember { mutableStateOf<ContactLink?>(null) }
    val faceShare = LocalFaceShare.current
    val sampledInk = remember(backgroundPixels) { backgroundPixels?.let(::identityInk) }
    val shareLabel = com.kigtts.next.i18n.uiText("profile.share_face")
    val ink = (backgroundInk ?: sampledInk)?.let(::Color) ?:
        if (!hasImage && Color(if (identity.gradient) identity.gradientColor else backgroundColor).luminance() > .42f) Color(0xFF202020) else Color.White
    val shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = .5f), androidx.compose.ui.geometry.Offset(0f, 1.5f), 4f)
    BoxWithConstraints(modifier) {
        val compact = maxWidth < 300.dp
        val padding = if (compact) 12.dp else 20.dp
        CompositionLocalProvider(LocalContentColor provides ink) {
            Crossfade(editing, Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 8.dp, end = 56.dp).fillMaxWidth(),
                tween(180), label = "profile-contact-edit") { edit ->
                if (edit) PersonalCardActionButton("编辑联系方式", "profile-edit-contacts", true, editInformation) {
                    Icon(Icons.Default.ContactPage, null, Modifier.size(22.dp), tint = Color.White)
                } else PersonalContactButtons(identity.contacts, interactive, { contact = it })
            }
            AnimatedVisibility(editing, Modifier.align(Alignment.TopEnd).padding(8.dp), enter = fadeIn(tween(180)), exit = fadeOut(tween(140))) {
                PersonalCardActionButton("编辑名片背景", "profile-edit-background", true, editBackground) {
                    Icon(Icons.Default.Wallpaper, null, Modifier.size(22.dp), tint = Color.White)
                }
            }
            if (!editing && interactive && faceShare != null) {
                FilledTonalButton(onClick = feedbackClick { faceShare.send(listOf(identity.designId)) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).height(36.dp).testTag("personal-face-share")
                        .semantics { contentDescription = shareLabel },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.Black.copy(alpha = .36f), contentColor = Color.White)) {
                    Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(com.kigtts.next.i18n.uiText("profile.share_face"))
                }
            }
            Row(Modifier.align(Alignment.BottomStart).padding(padding).animateContentSize(tween(220))
                .then(if (editing) Modifier.clickable(onClickLabel = "编辑头像与昵称", onClick = feedbackClick(editInformation))
                    .testTag("profile-edit-information") else Modifier), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PersonalAvatar(identity.avatar?.let(stickers::imageFile), Modifier.size(if (compact) 42.dp else 54.dp), identity.avatarFrame)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(identity.nickname.ifBlank { "我的名片" }, Modifier.weight(1f, fill = false), color = ink,
                            style = MaterialTheme.typography.titleLarge.copy(shadow = shadow), maxLines = 1)
                        AnimatedVisibility(editing, enter = fadeIn(tween(180)) + expandHorizontally(tween(220)),
                            exit = fadeOut(tween(140)) + shrinkHorizontally(tween(220))) {
                            Icon(Icons.Default.Edit, "编辑资料", Modifier.padding(start = 8.dp).size(18.dp))
                        }
                    }
                    if (identity.bio.isNotBlank()) Text(identity.bio, color = ink.copy(alpha = .86f), style = MaterialTheme.typography.bodyMedium.copy(shadow = shadow), maxLines = 2)
                }
            }
        }
    }
    contact?.let { PersonalContactDialog(it) { contact = null } }
}
