package com.kigtts.next.ui.plugins

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.kigtts.plugin.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable internal fun PluginMarkdown(block: PageBlock.Markdown, resolve: (PluginResource) -> File?) {
    val source = block.text.take(32000)
    val progressive = LocalProgressiveMarketContent.current
    val nodes = if (progressive) key(source) {
        produceState<List<org.commonmark.node.Node>?>(null, source) {
            value = withContext(Dispatchers.Default) { parseChatMarkdown(source).markdownChildren() }
        }.value
    } else null
    CompositionLocalProvider(LocalMarkdownImage provides { url, caption ->
        block.images[url]?.let { PluginPageImage(PageImage(it, caption), resolve, Modifier.fillMaxWidth().heightIn(max = 420.dp)) }
            ?: Text(caption.ifBlank { uiText("plugin.page_image_missing") }, style = MaterialTheme.typography.bodySmall)
    }) {
        SelectionContainer {
            if (progressive) MarketContentReveal(nodes != null, placeholder = { MarketPagePlaceholder() }) {
                nodes?.let { MarkdownNodes(it, small = false) }
            } else ChatRichText(source)
        }
    }
}
@Composable private fun PluginPageImage(image: PageImage, resolve: (PluginResource) -> File?, modifier: Modifier) {
    PreviewBitmap(image, previewBitmap(image, resolve), modifier)
}
@Composable internal fun PreviewBitmap(image: PageImage, preview: PreviewArtwork, modifier: Modifier) {
    Surface(modifier.clip(MaterialTheme.shapes.medium), color = MaterialTheme.colorScheme.surfaceContainer) {
        MarketContentReveal(!preview.loading, placeholder = {
            Box(Modifier.fillMaxWidth().height(160.dp))
        }) {
            val bitmap = preview.bitmap
            if (bitmap != null) Image(bitmap, image.description, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            else Box(Modifier.fillMaxWidth().height(160.dp).padding(16.dp)) {
                Text(image.description.ifBlank { uiText("plugin.page_preview_unavailable") }, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
