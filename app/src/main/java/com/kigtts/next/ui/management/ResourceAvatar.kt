package com.kigtts.next.ui.management

import com.kigtts.next.media.decodeResourceArtwork
import com.kigtts.next.media.isWhiteLineArtwork
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.kigtts.next.ui.components.vector
import com.kigtts.plugin.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun ResourceAvatar(item: ManagementItem, resolve: (PluginResource) -> File?, size: androidx.compose.ui.unit.Dp = 40.dp) {
    val file = item.image?.let { runCatching { resolve(it) }.getOrNull() }
    val version = file?.lastModified()
    val artwork by produceState<Pair<ImageBitmap, Boolean>?>(null, file?.path, version) {
        value = withContext(Dispatchers.IO) { decodeResourceArtwork(file)?.let { it.asImageBitmap() to it.isWhiteLineArtwork() } }
    }
    if (artwork != null) {
        val (bitmap, lineArt) = artwork!!
        Surface(Modifier.size(size).testTag("managed-avatar/" + item.id), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
            Image(bitmap, "${item.title}图标", Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium),
                colorFilter = if (lineArt) ColorFilter.tint(MaterialTheme.colorScheme.onSecondaryContainer) else null)
        }
    }
    else Surface(Modifier.size(size).testTag("managed-avatar/" + item.id), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(item.icon.vector(item.symbol), null, Modifier.size(size * .55f), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
    }
}
