package com.kigtts.next.ui.home

import com.kigtts.next.i18n.uiText

import com.kigtts.next.ui.components.MotionTextButton

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.saveable.Saver
import com.kigtts.next.ui.components.MaterialSymbolPicker
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.next.ui.components.dialogFeedbackClick
import com.kigtts.next.ui.components.rememberDialogClose
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
    var more by rememberSaveable(original) { mutableStateOf(false) }
    val iconSaver = remember { Saver<CategoryIcon, String>(save = { it.name }, restore = { runCatching { CategoryIcon.valueOf(it) }.getOrDefault(CategoryIcon.LIST) }) }
    var icon by rememberSaveable(original, stateSaver = iconSaver) { mutableStateOf(model.quickTexts.categoryIcons.value[original] ?: CategoryIcon.LIST) }
    val value = name.text.toString().trim()
    val duplicate = value in categories && value != original
    if (more) {
        MaterialSymbolPicker({ symbol -> icon = CategoryIcon.symbol(symbol); more = false }, { more = false }, icon.symbol)
        return
    }
    MotionAlertDialog(onDismissRequest = dismiss, title = { Text(if (original == null) "添加分组" else "编辑分组") },
        text = {
            Column(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(state = name, lineLimits = TextFieldLineLimits.SingleLine, label = { Text("分组名称") },
                    isError = duplicate, supportingText = { if (duplicate) Text(uiText("quick.group_duplicate")) },
                    modifier = Modifier.fillMaxWidth().testTag(if (original == null) "new-category-name" else "edit-category-name"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(icon.vector(), "当前图标：" + icon.label, Modifier.size(24.dp))
                        Text("分组图标", style = MaterialTheme.typography.titleSmall)
                    }
                    MotionTextButton({ more = true }, Modifier.testTag("category-more-icons")) { Text("更多与搜索") }
                }
                if (original != null) MotionTextButton(onClick = feedbackClick { model.sharing.shareQuickText(original, true) },
                    modifier = Modifier.fillMaxWidth().testTag("share-category")) {
                    Icon(androidx.compose.material.icons.Icons.Default.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text("分享分组")
                }
                LazyVerticalGrid(GridCells.Adaptive(48.dp), Modifier.fillMaxWidth().height(224.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(CategoryIcon.entries, key = { it.name }) { choice ->
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
        },
        confirmButton = { val closeDialog = rememberDialogClose(); MotionTextButton(onClick = feedbackClick {
            val saved = if (original == null) model.quickTexts.addCategory(value, icon) else model.quickTexts.editCategory(original, value, icon)
            if (saved) closeDialog { model.category(value); dismiss() }
        }, enabled = value.isNotBlank() && !duplicate, modifier = Modifier.testTag("save-category")) { Text(if (original == null) "添加" else "保存") } },
        dismissButton = { MotionTextButton(onClick = dialogFeedbackClick(dismiss)) { Text("取消") } })
}
