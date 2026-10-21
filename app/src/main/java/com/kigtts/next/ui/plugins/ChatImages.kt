package com.kigtts.next.ui.plugins

import android.graphics.BitmapFactory
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kigtts.plugin.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable internal fun ChatImages(images: List<ChatImage>, resolve: (PluginResource) -> File?, remove: ((Int) -> Unit)? = null) {
    var preview by remember { mutableStateOf<ChatImage?>(null) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        images.forEachIndexed { index, image -> key(image.source) { Box {
            ChatImageContent(image, resolve, Modifier.size(96.dp).clickable { preview = image })
            if (remove != null) IconButton(onClick = { remove(index) }) { Icon(Icons.Default.Close, "移除图片") }
        } } }
    }
    preview?.let { image -> Dialog(onDismissRequest = { preview = null }) {
        Surface(shape = MaterialTheme.shapes.large) { ChatImageContent(image, resolve, Modifier.fillMaxWidth().heightIn(max = 600.dp)) }
    } }
}
@Composable private fun ChatImageContent(image: ChatImage, resolve: (PluginResource) -> File?, modifier: Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, image.source) {
        value = withContext(Dispatchers.IO) { runCatching { resolve(image.source)?.let { file ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(file.path, options)
            var sample = 1; while (maxOf(options.outWidth, options.outHeight) / sample > 1536) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        } }.getOrNull() }
    }
    bitmap?.let { Image(it.asImageBitmap(), image.description, modifier) } ?: Box(modifier) { Text("图片加载中", style = MaterialTheme.typography.labelSmall) }
}
