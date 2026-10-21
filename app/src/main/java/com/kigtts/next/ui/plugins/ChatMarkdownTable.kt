package com.kigtts.next.ui.plugins

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.commonmark.ext.gfm.tables.*

@Composable internal fun ChatMarkdownTable(table: TableBlock, small: Boolean) {
    val rows = remember(table) { table.markdownChildren().flatMap { it.markdownChildren() }.filterIsInstance<TableRow>() }
    val cells = remember(rows) { rows.map { it.markdownChildren().filterIsInstance<TableCell>() } }
    val columns = cells.maxOfOrNull { it.size } ?: return
    val color = MaterialTheme.colorScheme.primary
    val text = cells.map { row -> row.map { markdownInline(it, color) } }
    val widths = remember(cells) { (0 until columns).map { column ->
        val units = cells.maxOf { row -> row.getOrNull(column)?.let { cell ->
            markdownInline(cell, color).text.sumOf { if (it.code > 255) 2L else 1L }.coerceAtMost(30).toInt()
        } ?: 0 }
        (units * 7 + 24).coerceIn(88, 240).dp
    } }
    Surface(Modifier.fillMaxWidth().testTag("chat-markdown-table"), shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            cells.forEachIndexed { rowIndex, row ->
                Row(Modifier.height(IntrinsicSize.Min)) {
                    repeat(columns) { column ->
                        val cell = row.getOrNull(column)
                        val header = cell?.isHeader == true
                        Box(Modifier.width(widths[column]).fillMaxHeight()
                            .background(if (header) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerLow).padding(10.dp)) {
                            Text(text[rowIndex].getOrNull(column) ?: androidx.compose.ui.text.AnnotatedString(""),
                                style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                                fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                                textAlign = when (cell?.alignment) { TableCell.Alignment.CENTER -> TextAlign.Center; TableCell.Alignment.RIGHT -> TextAlign.End; else -> TextAlign.Start },
                                modifier = Modifier.fillMaxWidth())
                        }
                        if (column < columns - 1) VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                if (rowIndex < cells.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
