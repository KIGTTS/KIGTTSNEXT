package com.kigtts.next.ui.home

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kigtts.next.media.MediaRepository
import com.kigtts.next.state.CategoryIcon
import com.kigtts.next.state.QuickPhrase
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.next.ui.components.dialogFeedbackClick
import com.kigtts.plugin.api.PluginOutcome
import com.kigtts.plugin.api.QuickTextSelectionMode

@Composable
fun QuickTextSelectorDialog(
    request: PluginOutcome.SelectQuickText,
    phrases: List<QuickPhrase>,
    categories: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
    categoryIcons: Map<String, CategoryIcon> = emptyMap(),
    repository: MediaRepository? = null,
) {
    var selected by remember(request) { mutableStateOf(request.selectedIds.toSet()) }
    val existing = remember(phrases) { phrases.mapTo(hashSetOf()) { it.id } }
    LaunchedEffect(existing) { selected = selected.intersect(existing) }
    MotionAlertDialog(
        onDismissRequest = onDismiss,
        dismissButton = { TextButton(onClick = dialogFeedbackClick(onDismiss)) { Text(uiText("action.cancel")) } },
        confirmButton = {
            Button(enabled = selected.any { it in existing }, onClick = dialogFeedbackClick {
                onConfirm(phrases.filter { it.id in selected }.map { it.id })
            }) { Text(if (request.mode == QuickTextSelectionMode.SINGLE) uiText("action.select") else uiText("action.done")) }
        },
        title = { Text(request.title) },
        text = {
            key(request) {
                QuickTextSelectorContent(phrases, categories, categoryIcons, repository, selected, request.mode,
                    { phrase -> selected = if (request.mode == QuickTextSelectionMode.SINGLE) setOf(phrase.id)
                        else selected.toMutableSet().apply { if (!add(phrase.id)) remove(phrase.id) } },
                    Modifier.fillMaxWidth().heightIn(max = 520.dp).fillMaxHeight())
            }
        },
    )
}