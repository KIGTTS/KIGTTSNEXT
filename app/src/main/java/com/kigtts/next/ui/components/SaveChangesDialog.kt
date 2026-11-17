package com.kigtts.next.ui.components

import com.kigtts.next.i18n.rememberUiCopy

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** All editors retain their validation and save path when leaving with changes. */
@Composable
fun SaveChangesDialog(save: (finish: (() -> Unit) -> Unit) -> Unit, discard: () -> Unit, keepEditing: () -> Unit,
    canSave: Boolean = true, busy: Boolean = false, error: String? = null, completed: (() -> Unit)? = null) {
    val copy = rememberUiCopy()
    MotionAlertDialog(onDismissRequest = { if (!busy) keepEditing() }, dismissEnabled = !busy, title = { Text(copy("dialog.save_changes")) },
        text = error?.let { message -> { Text(message) } },
        confirmButton = {
            val finish = rememberDialogClose()
            val completion = rememberUpdatedState(completed)
            LaunchedEffect(completed != null) { completion.value?.let(finish) }
            MotionTextButton(feedbackClick { save(finish) }, Modifier.testTag("save-changes-save"), enabled = canSave && !busy) {
                Text(copy("action.save"))
            }
        }, dismissButton = {
            Row {
                MotionTextButton(dialogFeedbackClick(discard), Modifier.testTag("save-changes-discard"), enabled = !busy) {
                    Text(copy("dialog.discard"))
                }
                MotionTextButton(dialogFeedbackClick(keepEditing), Modifier.testTag("save-changes-keep"), enabled = !busy) {
                    Text(copy("dialog.keep_editing"))
                }
            }
        })
}
