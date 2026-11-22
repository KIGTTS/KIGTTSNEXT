package com.kigtts.next.ui.plugins

import com.kigtts.next.i18n.uiText

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.kigtts.plugin.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable internal fun PluginMarkdown(block: PageBlock.Markdown, resolve: (PluginResource) -> File?) {
    CompositionLocalProvider(LocalMarkdownImage provides { url, caption ->
        block.images[url]?.let { PluginPageImage(PageImage(it, caption), resolve, Modifier.fillMaxWidth().heightIn(max = 420.dp)) }
            ?: Text(caption.ifBlank { uiText("plugin.page_image_missing") }, style = MaterialTheme.typography.bodySmall)
    }) { SelectionContainer { ChatRichText(block.text.take(32000)) } }
}

@Composable internal fun previewBitmap(image: PageImage, resolve: (PluginResource) -> File?): ImageBitmap? {
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
@Composable internal fun PreviewBitmap(image: PageImage, bitmap: ImageBitmap?, modifier: Modifier) {
    Surface(modifier.clip(MaterialTheme.shapes.medium), color = MaterialTheme.colorScheme.surfaceContainer) {
        if (bitmap != null) Image(bitmap, image.description, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        else Box(Modifier.fillMaxWidth().height(160.dp).padding(16.dp)) { Text(image.description.ifBlank { uiText("plugin.page_preview_unavailable") }, style = MaterialTheme.typography.bodySmall) }
    }
}
