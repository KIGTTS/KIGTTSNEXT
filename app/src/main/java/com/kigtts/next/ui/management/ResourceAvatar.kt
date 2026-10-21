package com.kigtts.next.ui.management

import android.graphics.BitmapFactory
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
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.vector
import com.kigtts.plugin.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun ResourceAvatar(item: ManagementItem, resolve: (PluginResource) -> File?, size: androidx.compose.ui.unit.Dp = 40.dp) {
    val file = item.image?.let { runCatching { resolve(it) }.getOrNull() }
    val version = file?.lastModified()
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, file?.path, version) {
        value = withContext(Dispatchers.IO) { runCatching {
            if (file == null || !file.isFile || file.length() > 8L * 1024 * 1024) return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth.toLong() * bounds.outHeight > 32_000_000) return@withContext null
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = maxOf(bounds.outWidth, bounds.outHeight) / 256 + 1 })?.asImageBitmap()
        }.getOrNull() }
    }
    if (bitmap != null) Image(bitmap!!, "${item.title}图标", Modifier.size(size).clip(MaterialTheme.shapes.medium))
    else Surface(Modifier.size(size), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(item.icon.vector(item.symbol), null, Modifier.size(size * .55f), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
    }
}
