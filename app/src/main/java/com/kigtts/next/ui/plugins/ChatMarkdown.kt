package com.kigtts.next.ui.plugins

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextDecoration
import org.commonmark.node.*
import org.commonmark.parser.Parser
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.gfm.strikethrough.*
import org.commonmark.ext.task.list.items.*
import org.commonmark.ext.autolink.AutolinkExtension

private val chatMarkdownParser = Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS).extensions(listOf(
    TablesExtension.create(), StrikethroughExtension.create(), TaskListItemsExtension.create(), AutolinkExtension.create())).build()

internal fun parseChatMarkdown(text: String): Node = chatMarkdownParser.parse(text)
/** Reuse closed blocks during streaming. Detach roots so old documents do not retain each other. */
internal class ChatMarkdownBlocks {
    private var previousText = "\u0000"
    private var previous = emptyList<Pair<String, Node>>()
    private var references = emptyList<String>()
    fun parse(text: String): List<Node> {
        if (text == previousText) return previous.map { it.second }
        val lines = text.lines()
        val nextReferences = lines.filter { it.trimStart().startsWith('[') && it.contains("]:") }
        if (nextReferences != references) previous = emptyList()
        references = nextReferences
        val nodes = parseChatMarkdown(text).markdownChildren()
        val next = nodes.mapIndexed { index, node ->
            val spans = node.sourceSpans
            val source = if (spans.isNotEmpty()) lines.subList(spans.first().lineIndex, (spans.last().lineIndex + 1).coerceAtMost(lines.size)).joinToString("\n") else "\u0000$index:$text"
            node.unlink()
            source to (previous.getOrNull(index)?.takeIf { it.first == source && it.second.javaClass == node.javaClass }?.second ?: node)
        }
        previousText = text; previous = next
        return next.map { it.second }
    }
}
internal fun Node.markdownChildren(): List<Node> = buildList {
    var child = firstChild
    while (child != null) { add(child); child = child.next }
}
internal fun safeMarkdownUrl(url: String) = url.startsWith("https://", true) || url.startsWith("http://", true) || url.startsWith("mailto:", true)

/** Native annotations retain selection; links open only when tapped, executable HTML stays text. */
internal fun markdownInline(node: Node, linkColor: Color): AnnotatedString = buildAnnotatedString {
    fun children(parent: Node, depth: Int) { parent.markdownChildren().forEach { child ->
        if (depth > 24) return@forEach
        fun nested() = children(child, depth + 1)
        when (child) {
            is Text -> append(child.literal)
            is Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = linkColor.copy(alpha = .1f))) { append(child.literal) }
            is StrongEmphasis -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { nested() }
            is Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { nested() }
            is Strikethrough -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { nested() }
            is Link -> if (safeMarkdownUrl(child.destination)) withLink(LinkAnnotation.Url(child.destination,
                TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)))) { nested() } else nested()
            is Image -> {
                append("图片：")
                if (safeMarkdownUrl(child.destination)) withLink(LinkAnnotation.Url(child.destination,
                    TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)))) { nested() } else nested()
            }
            is SoftLineBreak, is HardLineBreak -> append('\n')
            is HtmlInline -> append(child.literal)
            is TaskListItemMarker -> append(if (child.isChecked) "☑ " else "☐ ")
            else -> nested()
        }
    } }
    children(node, 0)
}
