package com.kigtts.next.ui.plugins

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/** A native, selectable Markdown subset. Code remains text and links use normal URL handling. */
@Composable internal fun ChatRichText(text: String, small: Boolean = false) {
    val blocks = remember(text) { markdownBlocks(text) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            val base = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge
            if (block.code) Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Text(block.text, Modifier.padding(12.dp).horizontalScroll(rememberScrollState()), style = base.copy(fontFamily = FontFamily.Monospace))
            } else Text(markdownInline(block.text, MaterialTheme.colorScheme.primary), style = when (block.level) {
                1 -> MaterialTheme.typography.headlineSmall; 2 -> MaterialTheme.typography.titleLarge; 3 -> MaterialTheme.typography.titleMedium; else -> base
            })
        }
    }
}
internal data class MarkdownBlock(val text: String, val code: Boolean = false, val level: Int = 0)
internal fun markdownBlocks(text: String): List<MarkdownBlock> {
    val result = mutableListOf<MarkdownBlock>(); var code = false; val buffer = StringBuilder()
    fun flush() { if (buffer.isNotEmpty()) { result += MarkdownBlock(buffer.toString().trimEnd(), code); buffer.clear() } }
    text.lineSequence().forEach { line ->
        if (line.startsWith("```")) { flush(); code = !code }
        else if (code) buffer.appendLine(line)
        else { flush(); val heading = line.takeWhile { it == '#' }.length
            result += if (heading in 1..6 && line.getOrNull(heading) == ' ') MarkdownBlock(line.drop(heading + 1), level = heading.coerceAtMost(3))
                else MarkdownBlock(if (line.startsWith("- ") || line.startsWith("* ")) "• " + line.drop(2) else line)
        }
    }; flush(); return result
}
internal fun markdownInline(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    val pattern = Regex("\\[([^]]+)\\]\\((https?://[^ )]+)\\)|\\*\\*(.+?)\\*\\*|__(.+?)__|`([^`]+)`|\\*([^*]+)\\*")
    var cursor = 0
    pattern.findAll(text).forEach { match ->
        append(text.substring(cursor, match.range.first))
        val group = match.groupValues
        when {
            group[1].isNotEmpty() -> withLink(LinkAnnotation.Url(group[2], TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)))) { append(group[1]) }
            group[3].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(group[3]) }
            group[4].isNotEmpty() -> withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(group[4]) }
            group[5].isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(group[5]) }
            else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(group[6]) }
        }
        cursor = match.range.last + 1
    }; append(text.substring(cursor))
}
