package com.kigtts.next

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.kigtts.next.ui.plugins.*
import org.commonmark.node.*
import org.commonmark.ext.gfm.tables.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalTextApi::class)
class ChatMarkdownTest {
    @Test fun streamingRetainsClosedBlocksButUpdatesTheGrowingTailAndFormatting() {
        val cache = ChatMarkdownBlocks()
        val first = cache.parse("# 保留标题\n\n已完成的 **段落**。\n\n正在")
        val second = cache.parse("# 保留标题\n\n已完成的 **段落**。\n\n正在生成")
        assertSame(first[0], second[0]); assertSame(first[1], second[1]); assertNotSame(first[2], second[2])
        assertTrue(second.all { it.parent == null })
        val table = cache.parse("# 保留标题\n\n|A|B|\n|---|---|\n|1|2|")
        assertSame(first[0], table[0]); assertTrue(table[1] is TableBlock)
    }
    @Test fun tableCellsRespectEscapedPipesInlineFormattingAndAlignment() {
        val table = parseChatMarkdown("| 项目 | 数量 |\n| :--- | ---: |\n| A\\|B | **两份** |").firstChild as TableBlock
        val rows = table.markdownChildren().flatMap { it.markdownChildren() }.filterIsInstance<TableRow>()
        val cells = rows.last().markdownChildren().filterIsInstance<TableCell>()
        assertEquals(2, cells.size)
        assertEquals("A|B", markdownInline(cells[0], Color.Blue).text)
        assertEquals(TableCell.Alignment.RIGHT, cells[1].alignment)
        assertTrue(markdownInline(cells[1], Color.Blue).spanStyles.any { it.item.fontWeight == FontWeight.Bold })
    }
    @Test fun commonAiBlocksNestedStylesAndLinksRemainNativeAndUnsafeLinksAreInert() {
        val doc = parseChatMarkdown("# 标题\n\n> 引用\n\n1. **粗体和*斜体***\n2. ~~删除~~\n\n---\n\n- [x] 完成\n- [ ] 待办\n\n[安全](https://example.com) [坏链接](javascript:alert(1))")
        val blocks = doc.markdownChildren()
        assertTrue(blocks.any { it is Heading }); assertTrue(blocks.any { it is BlockQuote })
        assertTrue(blocks.any { it is OrderedList }); assertTrue(blocks.any { it is ThematicBreak })
        val list = blocks.filterIsInstance<BulletList>().single()
        assertTrue((list.firstChild.firstChild as org.commonmark.ext.task.list.items.TaskListItemMarker).isChecked)
        val link = markdownInline(blocks.last(), Color.Blue)
        assertEquals(1, link.getLinkAnnotations(0, link.length).size)
        val ordered = blocks.filterIsInstance<OrderedList>().single()
        val strike = markdownInline(ordered.lastChild.firstChild, Color.Blue)
        assertTrue(strike.spanStyles.any { it.item.textDecoration == TextDecoration.LineThrough })
    }
    @Test fun streamingUnclosedCodeAndIncompleteTableStayReadable() {
        val code = parseChatMarkdown("```kotlin\nval value = 1\n").firstChild as FencedCodeBlock
        assertEquals("kotlin", code.info); assertEquals("val value = 1\n", code.literal)
        assertTrue(parseChatMarkdown("| 项目 | 数量 |\n| --").firstChild is Paragraph)
        val paragraph = parseChatMarkdown("https://example.com/path").firstChild
        assertEquals(1, markdownInline(paragraph, Color.Blue).getLinkAnnotations(0, 30).size)
    }
}
