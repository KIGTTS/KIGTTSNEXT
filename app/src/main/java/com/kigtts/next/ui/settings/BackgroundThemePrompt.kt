package com.kigtts.next.ui.settings

import com.kigtts.next.i18n.uiText

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
    MotionAlertDialog(onDismissRequest = { onResult(false, !never) }, title = { Text(uiText("theme.use_background")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(uiText("theme.match_background"))
            Row(Modifier.fillMaxWidth().toggleable(never, role = Role.Checkbox, onValueChange = feedbackChange { never = it }),
                verticalAlignment = Alignment.CenterVertically) {
                Checkbox(never, null); Spacer(Modifier.width(8.dp)); Text(uiText("theme.skip_prompt"))
            }
        }
    }, confirmButton = { TextButton(dialogFeedbackClick { onResult(true, !never) }) { Text(uiText("theme.switch")) } },
        dismissButton = { TextButton(dialogFeedbackClick { onResult(false, !never) }) { Text(uiText("theme.keep_colors")) } })
}
