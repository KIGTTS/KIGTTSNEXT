package com.kigtts.next.ui.plugins

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.commonmark.node.*
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.task.list.items.TaskListItemMarker

@Composable internal fun ChatRichText(text: String, small: Boolean = false) {
    val cache = remember { ChatMarkdownBlocks() }
    val blocks = remember(text) { cache.parse(text) }
    MarkdownNodes(blocks, small)
}

internal val LocalMarkdownImage = staticCompositionLocalOf<(@Composable (String, String) -> Unit)?> { null }

@Composable private fun MarkdownBlocks(parent: Node, small: Boolean, depth: Int = 0) {
    MarkdownNodes(remember(parent) { parent.markdownChildren() }, small, depth)
}
@Composable internal fun MarkdownNodes(nodes: List<Node>, small: Boolean, depth: Int = 0) {
    if (depth > 20) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        nodes.filterNot { it is TaskListItemMarker }.forEach { node -> key(node) { MarkdownBlock(node, small, depth) } }
    }
}
@Composable private fun MarkdownBlock(node: Node, small: Boolean, depth: Int) {
    val style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge
    val color = MaterialTheme.colorScheme.primary
    when (node) {
            is Paragraph -> {
                val image = node.firstChild as? Image
                val renderer = LocalMarkdownImage.current
                if (image != null && image.next == null && renderer != null) renderer(image.destination, markdownInline(image, color).text)
                else Text(remember(node, color) { markdownInline(node, color) }, style = style)
            }
            is Heading -> Text(remember(node, color) { markdownInline(node, color) }, style = when (node.level) {
                1 -> MaterialTheme.typography.headlineSmall; 2 -> MaterialTheme.typography.titleLarge
                3 -> MaterialTheme.typography.titleMedium; else -> MaterialTheme.typography.titleSmall
            })
            is FencedCodeBlock -> ChatMarkdownCode(node.literal, node.info.orEmpty(), small)
            is IndentedCodeBlock -> ChatMarkdownCode(node.literal, "", small)
            is TableBlock -> ChatMarkdownTable(node, small)
            is ThematicBreak -> HorizontalDivider(Modifier.padding(vertical = 4.dp))
            is BlockQuote -> Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                Box(Modifier.weight(1f).padding(start = 12.dp)) { MarkdownBlocks(node, small, depth + 1) }
            }
            is BulletList, is OrderedList -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                node.markdownChildren().forEachIndexed { index, item ->
                    val task = item.firstChild as? TaskListItemMarker
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (task != null) { if (task.isChecked) "☑" else "☐" }
                            else if (node is OrderedList) "${node.markerStartNumber + index}." else "•", style = style)
                        Box(Modifier.weight(1f)) { MarkdownBlocks(item, small, depth + 1) }
                    }
                }
            }
            is HtmlBlock -> Text(node.literal, style = style)
            else -> MarkdownBlocks(node, small, depth + 1)
    }
}
