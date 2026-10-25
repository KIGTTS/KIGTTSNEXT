package com.kigtts.next.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*

@Composable internal fun BackgroundThemePrompt(onResult: (switch: Boolean, remind: Boolean) -> Unit) {
    var never by rememberSaveable { mutableStateOf(false) }
    MotionAlertDialog(onDismissRequest = { onResult(false, !never) }, title = { Text("使用背景主题色？") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("让按钮和面板配色与这张背景相配。")
            Row(Modifier.fillMaxWidth().toggleable(never, role = Role.Checkbox, onValueChange = feedbackChange { never = it }),
                verticalAlignment = Alignment.CenterVertically) {
                Checkbox(never, null); Spacer(Modifier.width(8.dp)); Text("下次不再提醒")
            }
        }
    }, confirmButton = { TextButton(dialogFeedbackClick { onResult(true, !never) }) { Text("切换") } },
        dismissButton = { TextButton(dialogFeedbackClick { onResult(false, !never) }) { Text("保留当前配色") } })
}
