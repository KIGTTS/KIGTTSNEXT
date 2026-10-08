package com.kigtts.next.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.feedbackClick

@Composable
fun AddCategoryDialog(categories: List<String>, model: AppViewModel, dismiss: () -> Unit) {
    CategoryPropertiesDialog(null, categories, model, dismiss)
}

@Composable
fun EditCategoryDialog(original: String, categories: List<String>, model: AppViewModel, dismiss: () -> Unit) {
    CategoryPropertiesDialog(original, categories, model, dismiss)
}

@Composable
private fun CategoryPropertiesDialog(original: String?, categories: List<String>, model: AppViewModel, dismiss: () -> Unit) {
    val name = rememberTextFieldState(original.orEmpty())
    var icon by rememberSaveable(original) { mutableStateOf(model.quickTexts.categoryIcons.value[original] ?: CategoryIcon.LIST) }
    val value = name.text.toString().trim()
    val duplicate = value in categories && value != original
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (original == null) "添加分组" else "编辑分组") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(state = name, lineLimits = TextFieldLineLimits.SingleLine, label = { Text("分组名称") },
                    isError = duplicate, supportingText = { if (duplicate) Text("分组名称已存在") },
                    modifier = Modifier.fillMaxWidth().testTag(if (original == null) "new-category-name" else "edit-category-name"))
                Text("分组图标 · " + icon.label, style = MaterialTheme.typography.titleSmall)
                CategoryIcon.entries.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        row.forEach { choice ->
                            FilledTonalIconButton(onClick = feedbackClick { icon = choice }, shape = CircleShape,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = if (icon == choice) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                    contentColor = if (icon == choice) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant),
                                modifier = Modifier.size(48.dp).testTag("choose-category-icon-" + choice.name).semantics {
                                    selected = icon == choice; role = Role.RadioButton
                                }) { Icon(choice.vector(), "分组图标：" + choice.label) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = feedbackClick {
            val saved = if (original == null) model.quickTexts.addCategory(value, icon) else model.quickTexts.editCategory(original, value, icon)
            if (saved) { model.category(value); dismiss() }
        }, enabled = value.isNotBlank() && !duplicate, modifier = Modifier.testTag("save-category")) { Text(if (original == null) "添加" else "保存") } },
        dismissButton = { TextButton(onClick = feedbackClick(dismiss)) { Text("取消") } })
}
