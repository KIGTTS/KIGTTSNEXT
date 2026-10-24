@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.kigtts.next.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.QuickPhrase
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.next.ui.components.dialogFeedbackClick
import com.kigtts.next.ui.media.PhraseMediaBackdrop
import com.kigtts.plugin.api.PluginOutcome
import com.kigtts.plugin.api.QuickTextSelectionMode

/** Read-only picker shared by plugins. It deliberately reuses the quick-text card language. */
@Composable
fun QuickTextSelectorDialog(
    request: PluginOutcome.SelectQuickText,
    phrases: List<QuickPhrase>,
    categories: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    var category by remember(request, categories) { mutableStateOf(categories.firstOrNull().orEmpty()) }
    var selected by remember(request) { mutableStateOf(request.selectedIds.toSet()) }
    LaunchedEffect(categories) {
        if (category !in categories) category = categories.firstOrNull().orEmpty()
    }
    val visible = remember(phrases, category) { phrases.filter { it.category == category } }
    MotionAlertDialog(
        onDismissRequest = onDismiss,
        dismissButton = { TextButton(onClick = dialogFeedbackClick(onDismiss)) { Text("取消") } },
        confirmButton = {
            Button(
                enabled = selected.isNotEmpty(),
                onClick = dialogFeedbackClick { onConfirm(selected.toList()) },
            ) { Text(if (request.mode == QuickTextSelectionMode.SINGLE) "选择" else "完成") }
        },
        title = { Text(request.title) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 520.dp)) {
                LazyRow(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(categories.size) { index ->
                        val item = categories[index]
                        FilterChip(
                            selected = item == category,
                            onClick = { category = item },
                            label = { Text(item, maxLines = 1) },
                        )
                    }
                }
                if (visible.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("这个分组还没有快捷文本", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(148.dp),
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 4.dp),
                    ) {
                        items(visible, key = { it.id }) { phrase ->
                            SelectorCard(
                                phrase = phrase,
                                selected = phrase.id in selected,
                                onClick = {
                                    selected = if (request.mode == QuickTextSelectionMode.SINGLE) setOf(phrase.id)
                                    else selected.toMutableSet().apply { if (!add(phrase.id)) remove(phrase.id) }
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun SelectorCard(phrase: QuickPhrase, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    Surface(
        onClick = onClick,
        modifier = Modifier.heightIn(min = QuickPhraseHeight),
        shape = MaterialTheme.shapes.medium,
        color = quickPhraseColor(phrase),
        contentColor = quickPhraseContentColor(phrase),
        border = border,
    ) {
        Box(Modifier.fillMaxSize()) {
            PhraseMediaBackdrop(phrase, null, Modifier.matchParentSize(), quickPhraseColor(phrase))
            QuickPhraseText(phrase, Modifier.fillMaxWidth().padding(12.dp))
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Check, "已选", tint = MaterialTheme.colorScheme.onPrimary) }
            }
        }
    }
}
