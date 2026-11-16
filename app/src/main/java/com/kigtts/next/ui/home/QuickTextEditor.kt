@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.home

import com.kigtts.next.i18n.uiText

import com.kigtts.next.ui.components.reorder.*

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.next.ui.components.dialogFeedbackClick
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.navigation.LocalPageActive
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun QuickTextEditor(state: AppState, phrases: List<QuickPhrase>, categories: List<String>, model: AppViewModel,
    modifier: Modifier = Modifier) {
    val active = LocalPageActive.current
    var addGroup by remember { mutableStateOf(false) }
    var editGroup by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<QuickPhrase?>(null) }
    var menu by remember { mutableStateOf<QuickTextMenuTarget?>(null) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val feedback = rememberDragFeedback()
    val drag = rememberReorderDrag({ emptyList() }, animateDrop = true, feedback = feedback, move = model.quickTexts::movePhrase)
    val density = LocalDensity.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val icons by model.quickTexts.categoryIcons.collectAsStateWithLifecycle()
    val category = state.selectedCategory
    val addPhrase = { model.editPhraseDetails(null, category) }
    Surface(modifier.fillMaxSize().testTag("quick-text-editor"), shape = homeCardShape(),
        color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolButton("完成编辑", Icons.Default.Check, {
                    focus.clearFocus(); keyboard?.hide(); model.finishQuickTextEditing()
                }, Modifier.testTag("quick-edit-done"))
                Box(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    TextButton(onClick = feedbackClick { editGroup = category }, modifier = Modifier.testTag("quick-edit-group"),
                        contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Icon((icons[category] ?: CategoryIcon.defaultFor(category)).vector(), null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(category, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.Edit, uiText("quick.edit_group"), Modifier.size(16.dp))
                    }
                }
                ToolButton("新增快捷文本", Icons.Default.Add, addPhrase, Modifier.testTag("quick-edit-add"))
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().testTag("quick-editor-pane")
                .reorderGestures(drag, onStationaryHold = { menu = it.menuTarget() })) {
                val columns = quickPhraseColumns(maxWidth.value, minimum = if (landscape) 1 else 2)
                Column(Modifier.fillMaxSize()) {
                    CategoryContentMotion(state.selectedCategory, categories, drag.key == null, model::category, Modifier.weight(1f)) { category, active ->
                        EditablePhraseGrid(phrases.filter { it.category == category }, model,
                            { model.editPhraseDetails(it, state.selectedCategory) }, { deleting = it },
                            addPhrase,
                            Modifier.fillMaxSize(), drag = drag, active = active, columns = columns)
                    }
                    CategoryTabs(state, categories, model, editing = true, addGroup = { addGroup = true }, editGroup = { editGroup = it })
                }
                PhraseDragOverlay(drag, phrases, state.selectedCategory, categories, with(density) { maxWidth.toPx() }, model, columns)
            }
        }
    }
    if (addGroup) AddCategoryDialog(categories, model) { addGroup = false }
    QuickTextContextMenu(menu, model, { menu = null }, { editGroup = it })
    editGroup?.let { EditCategoryDialog(it, categories, model) { editGroup = null } }
    deleting?.let { phrase ->
        MotionAlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除这条快捷文本？") },
            text = { Text(phrase.text) },
            confirmButton = { TextButton(onClick = dialogFeedbackClick { model.quickTexts.remove(phrase.id); deleting = null },
                modifier = Modifier.testTag("confirm-delete-phrase")) { Text(uiText("action.delete")) } },
            dismissButton = { TextButton(onClick = dialogFeedbackClick { deleting = null }, modifier = Modifier.testTag("cancel-delete-phrase")) { Text(uiText("action.cancel")) } })
    }
}
