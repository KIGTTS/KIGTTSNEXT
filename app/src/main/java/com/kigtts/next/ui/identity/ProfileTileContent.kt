package com.kigtts.next.ui.identity

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kigtts.next.identity.*
import java.io.File

@Composable internal fun ProfileTileContent(tile: ProfileTile, contact: ContactLink?, image: (String) -> File?, editing: Boolean) {
    if (tile.kind == ProfileTileKind.QR) {
        ProfileQrTile(tile.text, tile.qrStyle, tile.title, Modifier.fillMaxSize().padding(8.dp)); return
    }
    if (tile.kind == ProfileTileKind.IMAGE && tile.image.isNotBlank()) {
        ProfileTileImage(image(tile.image), Modifier.fillMaxSize(), tile.title.ifBlank { "图片" }); return
    }
    if (tile.kind == ProfileTileKind.TITLE) {
        Text(tile.title, Modifier.padding(start = if (editing) 36.dp else 4.dp, top = 4.dp, end = 4.dp),
            style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis); return
    }
    if (tile.isTitleOnlyText) {
        Box(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(tile.title, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge.copy(lineBreak = LineBreak.Heading),
                textAlign = TextAlign.Center, maxLines = tile.rows * 2, overflow = TextOverflow.Ellipsis)
        }
        return
    }
    val hasIcon = tile.kind == ProfileTileKind.CONTACT || tile.kind == ProfileTileKind.LINK
    val mode = if (hasIcon && tile.columns == 1 && tile.rows == 1) 0 else if (tile.rows == 1) 1 else 2
    val title = tile.title.ifBlank { contact?.label ?: if (tile.kind == ProfileTileKind.STATUS) "状态" else "" }
    val detail = if (tile.kind == ProfileTileKind.CONTACT) contact?.value ?: "联系方式已移除" else tile.text
    Crossfade(mode, Modifier.fillMaxSize(), tween(120), label = "tile-content-layout") { layout ->
        val icon: @Composable () -> Unit = {
            if (tile.kind == ProfileTileKind.CONTACT) SocialContactIcon(contact ?: ContactLink("", ""), Modifier.size(if (layout == 2) 28.dp else 24.dp))
            if (tile.kind == ProfileTileKind.LINK) Icon(Icons.Default.Link, null, Modifier.size(24.dp))
        }
        when (layout) {
            0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { icon() }
            1 -> Row(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                icon()
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(detail, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
                icon()
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(detail, style = MaterialTheme.typography.bodyMedium, maxLines = tile.rows * 2, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
