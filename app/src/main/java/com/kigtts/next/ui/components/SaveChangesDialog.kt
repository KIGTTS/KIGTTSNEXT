package com.kigtts.next.ui.components

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
    MotionAlertDialog(onDismissRequest = { if (!busy) keepEditing() }, dismissEnabled = !busy, title = { Text("保存修改？") },
        text = error?.let { message -> { Text(message) } },
        confirmButton = {
            val finish = rememberDialogClose()
            val completion = rememberUpdatedState(completed)
            LaunchedEffect(completed != null) { completion.value?.let(finish) }
            MotionTextButton(feedbackClick { save(finish) }, Modifier.testTag("save-changes-save"), enabled = canSave && !busy) {
                Text("保存")
            }
        }, dismissButton = {
            Row {
                MotionTextButton(dialogFeedbackClick(discard), Modifier.testTag("save-changes-discard"), enabled = !busy) {
                    Text("不保存")
                }
                MotionTextButton(dialogFeedbackClick(keepEditing), Modifier.testTag("save-changes-keep"), enabled = !busy) {
                    Text("继续编辑")
                }
            }
        })
}
