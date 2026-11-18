package com.kigtts.next.ui.cards

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*

@Composable
internal fun CardNameDialog(title: String, initial: String, dismiss: () -> Unit, confirm: (String) -> Unit,
    remove: (() -> Unit)? = null, multiline: Boolean = false, maximum: Int = 128,
    trimInput: Boolean = true, inputLabel: String = if (multiline) "文字" else "名称") {
    var value by remember(initial) { mutableStateOf(initial) }
    MotionAlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value, { if (it.length <= maximum) value = it }, Modifier.fillMaxWidth().testTag("card-name-input"),
                singleLine = !multiline, minLines = if (multiline) 2 else 1, label = { Text(inputLabel) })
            remove?.let { MotionTextButton(feedbackClick(it)) { Icon(Icons.Default.DeleteOutline, null); Spacer(Modifier.width(8.dp)); Text(uiText("photo.delete_library")) } }
        }
    }, confirmButton = { MotionTextButton(dialogFeedbackClick { confirm(if (trimInput) value.trim() else value) }, enabled = value.isNotBlank()) { Text(uiText("common.confirm")) } },
        dismissButton = { MotionTextButton(dialogFeedbackClick(dismiss)) { Text(uiText("action.cancel")) } })
}

@Composable
internal fun CardDeleteDialog(title: String, detail: String, dismiss: () -> Unit, confirm: () -> Unit) {
    MotionAlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(detail) },
        confirmButton = { MotionTextButton(dialogFeedbackClick(confirm)) { Text(uiText("action.delete")) } },
        dismissButton = { MotionTextButton(dialogFeedbackClick(dismiss)) { Text(uiText("action.cancel")) } })
}
