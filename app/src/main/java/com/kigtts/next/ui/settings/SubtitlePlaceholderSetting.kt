package com.kigtts.next.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import com.kigtts.next.state.SubtitlePlaceholder
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.next.ui.components.dialogFeedbackClick
import com.kigtts.next.ui.navigation.LocalPageActive

@Composable
internal fun SubtitlePlaceholderSetting(placeholder: String, onChange: (String) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf(placeholder) }
    SettingsGroup("字幕") {
        SettingItem("空白提示", placeholder.takeIf { it.isNotBlank() } ?: "不显示", Icons.Default.TextFields, SettingPosition.SINGLE,
            Modifier.testTag("subtitle-placeholder-setting"), onClick = { draft = placeholder; editing = true })
    }
    if (editing && LocalPageActive.current) MotionAlertDialog(onDismissRequest = { editing = false },
        title = { Text("空白提示") }, text = {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { withFrameNanos { }; focus.requestFocus() }
            OutlinedTextField(draft, { draft = SubtitlePlaceholder.normalize(it) }, Modifier.fillMaxWidth().testTag("subtitle-placeholder-input").focusRequester(focus),
                minLines = 2, maxLines = 5, label = { Text("提示文字") },
                supportingText = { Text("留空则不显示提示") })
        }, confirmButton = {
            TextButton(dialogFeedbackClick { onChange(SubtitlePlaceholder.normalize(draft)); editing = false },
                Modifier.testTag("subtitle-placeholder-save")) { Text("保存") }
        }, dismissButton = {
            TextButton(dialogFeedbackClick { editing = false }, Modifier.testTag("subtitle-placeholder-cancel")) { Text("取消") }
        })
}
