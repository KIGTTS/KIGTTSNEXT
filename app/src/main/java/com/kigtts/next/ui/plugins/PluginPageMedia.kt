package com.kigtts.next.ui.plugins

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import com.kigtts.plugin.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable internal fun PluginMarkdown(block: PageBlock.Markdown, resolve: (PluginResource) -> File?) {
    CompositionLocalProvider(LocalMarkdownImage provides { url, caption ->
        block.images[url]?.let { PluginPageImage(PageImage(it, caption), resolve, Modifier.fillMaxWidth().heightIn(max = 420.dp)) }
            ?: Text(caption.ifBlank { "图片未提供" }, style = MaterialTheme.typography.bodySmall)
    }) { SelectionContainer { ChatRichText(block.text.take(32000)) } }
}

@Composable internal fun PluginGallery(block: PageBlock.Gallery, resolve: (PluginResource) -> File?) {
    var expanded by remember { mutableStateOf<PageImage?>(null) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(block.images.take(8)) { image ->
            val bitmap = previewBitmap(image, resolve)
            val ratio = previewRatio(image, bitmap)
            val width = (260.dp * ratio).coerceIn(64.dp, 560.dp)
            val height = (width / ratio).coerceIn(64.dp, 320.dp)
            Column(Modifier.width(width).animateContentSize(tween(160)), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PreviewBitmap(image, bitmap, Modifier.fillMaxWidth().height(height).clickable { expanded = image })
                if (image.description.isNotBlank()) Text(image.description, style = MaterialTheme.typography.labelMedium, maxLines = 2)
            }
        }
    }
    expanded?.let { image -> Dialog({ expanded = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = .9f)).padding(16.dp)) {
            PreviewBitmap(image, previewBitmap(image, resolve), Modifier.fillMaxSize())
            TextButton({ expanded = null }, Modifier.align(Alignment.TopEnd)) { Text("关闭", color = Color.White) }
        }
    } }
}

internal fun previewRatio(image: PageImage, bitmap: ImageBitmap?): Float =
    if (image.width != null && image.height != null) image.width!!.toFloat() / image.height!!
    else if (bitmap != null) bitmap.width.toFloat() / bitmap.height else 1f

@Composable private fun previewBitmap(image: PageImage, resolve: (PluginResource) -> File?): ImageBitmap? {
    val file = remember(image.resource) { runCatching { resolve(image.resource) }.getOrNull() }
    val bitmap by produceState<ImageBitmap?>(null, file?.path, file?.lastModified()) {
        value = withContext(Dispatchers.IO) { runCatching {
            if (file == null || !file.isFile || file.length() > 8L * 1024 * 1024) return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext com.kigtts.next.media.UiIcons.renderPreviewSvg(file.readText(Charsets.UTF_8), 1200).asImageBitmap()
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = maxOf(bounds.outWidth, bounds.outHeight) / 1200 + 1 })?.asImageBitmap()
        }.getOrNull() }
    }
    return bitmap
}
@Composable private fun PluginPageImage(image: PageImage, resolve: (PluginResource) -> File?, modifier: Modifier) {
    PreviewBitmap(image, previewBitmap(image, resolve), modifier)
}
@Composable private fun PreviewBitmap(image: PageImage, bitmap: ImageBitmap?, modifier: Modifier) {
    Surface(modifier.clip(MaterialTheme.shapes.medium), color = MaterialTheme.colorScheme.surfaceContainer) {
        if (bitmap != null) Image(bitmap, image.description, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        else Box(Modifier.fillMaxWidth().height(160.dp).padding(16.dp)) { Text(image.description.ifBlank { "预览图暂不可用" }, style = MaterialTheme.typography.bodySmall) }
    }
}
