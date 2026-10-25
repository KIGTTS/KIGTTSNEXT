package com.kigtts.next.ui.identity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kigtts.next.cards.StickerRepository
import com.kigtts.next.identity.*
import com.kigtts.next.ui.components.*

/** Reused by the card page, personal-information preview and the live background editor. */
@Composable internal fun PersonalCardOverlay(identity: IdentityCard, stickers: StickerRepository,
    modifier: Modifier = Modifier, interactive: Boolean = true, backgroundColor: Long = 0xFF245E63,
    hasImage: Boolean = true) {
    var contact by remember { mutableStateOf<ContactLink?>(null) }
    val ink = if (!hasImage && !identity.gradient && Color(backgroundColor).luminance() > .5f) Color(0xFF202020) else Color.White
    BoxWithConstraints(modifier) {
        val compact = maxWidth < 300.dp
        val padding = if (compact) 12.dp else 20.dp
        if (identity.gradient) Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .78f)))))
        CompositionLocalProvider(LocalContentColor provides ink) {
            LazyRow(Modifier.align(Alignment.TopStart).padding(8.dp).fillMaxWidth().testTag("personal-card-contacts"),
                horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(identity.contacts) { index, link ->
                    val label = "联系方式：" + link.label.ifBlank { "链接" }
                    if (interactive) IconActionHint(label) {
                        IconButton(feedbackClick { contact = link }, Modifier.size(40.dp).testTag("personal-contact-" + index)
                            .iconLongAction(true, null).semantics { contentDescription = label }) { SocialContactIcon(link, Modifier.size(22.dp)) }
                    } else Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { SocialContactIcon(link, Modifier.size(22.dp)) }
                }
            }
            Row(Modifier.align(Alignment.BottomStart).padding(padding), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PersonalAvatar(identity.avatar?.let(stickers::imageFile), Modifier.size(if (compact) 42.dp else 54.dp))
                Column(Modifier.weight(1f)) {
                    Text(identity.nickname.ifBlank { "我的名片" }, color = ink, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                    if (identity.bio.isNotBlank()) Text(identity.bio, color = ink.copy(alpha = .86f), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                }
            }
        }
    }
    contact?.let { PersonalContactDialog(it) { contact = null } }
}
