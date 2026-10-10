@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.home

import com.kigtts.next.ui.components.reorder.*

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.navigation.LocalPageActive
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun QuickTextEditor(state: AppState, phrases: List<QuickPhrase>, categories: List<String>, model: AppViewModel,
    modifier: Modifier = Modifier) {
    val draft by model.content.phraseDraft.collectAsStateWithLifecycle()
    val active = LocalPageActive.current
    val anchors = remember { mutableStateMapOf<String, PhraseDialogAnchor>() }
    var openingAnchor by remember { mutableStateOf<PhraseDialogAnchor?>(null) }
    var addGroup by remember { mutableStateOf(false) }
    var editGroup by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<QuickPhrase?>(null) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val drag = rememberReorderDrag({ emptyList() }, animateDrop = true, move = model.quickTexts::movePhrase)
    val density = LocalDensity.current
    Surface(modifier.fillMaxSize().testTag("quick-text-editor"), shape = homeCardShape(),
        color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolButton("完成编辑", Icons.Default.Check, {
                    focus.clearFocus(); keyboard?.hide(); model.finishQuickTextEditing()
                }, Modifier.testTag("quick-edit-done"))
                Text("编辑快捷文本", Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.titleMedium)
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().testTag("quick-editor-pane").reorderGestures(drag)) {
                Column(Modifier.fillMaxSize()) {
                    CategoryContentMotion(state.selectedCategory, categories, drag.key == null, model::category, Modifier.weight(1f)) { category, active ->
                        EditablePhraseGrid(phrases.filter { it.category == category }, model,
                            { openingAnchor = anchors[it.id]; model.content.beginPhrase(it, state.selectedCategory) }, { deleting = it },
                            { openingAnchor = anchors["add"]; model.content.beginPhrase(null, state.selectedCategory) },
                            Modifier.fillMaxSize(), drag = drag, active = active, hiddenPhraseId = draft?.phraseId,
                            onBounds = { id, bounds -> anchors[id] = bounds }, onAddBounds = { anchors["add"] = it })
                    }
                    CategoryTabs(state, categories, model, editing = true, addGroup = { addGroup = true }, editGroup = { editGroup = it })
                }
                PhraseDragOverlay(drag, phrases, state.selectedCategory, categories, with(density) { maxWidth.toPx() }, model)
            }
        }
    }
    draft?.let { key(it.key) { PhraseDetailsDialog(it, categories, model, model.content::cancelPhrase,
        anchor = anchors[it.phraseId ?: "add"] ?: openingAnchor, visible = active) } }
    if (addGroup) AddCategoryDialog(categories, model) { addGroup = false }
    editGroup?.let { EditCategoryDialog(it, categories, model) { editGroup = null } }
    deleting?.let { phrase ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除这条快捷文本？") },
            text = { Text(phrase.text) },
            confirmButton = { TextButton(onClick = feedbackClick { model.quickTexts.remove(phrase.id); deleting = null },
                modifier = Modifier.testTag("confirm-delete-phrase")) { Text("删除") } },
            dismissButton = { TextButton(onClick = feedbackClick { deleting = null }, modifier = Modifier.testTag("cancel-delete-phrase")) { Text("取消") } })
    }
}
