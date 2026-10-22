package com.kigtts.next.ui.home

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.state.QuickSortPreference
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.next.ui.components.dialogFeedbackClick

@Composable
internal fun QuickSortPrompt(preference: QuickSortPreference) {
    val show by preference.prompt.collectAsStateWithLifecycle()
    if (show) MotionAlertDialog(onDismissRequest = preference::dismiss,
        title = { Text("开启快速排序？") },
        text = { Text("开启后，不用进入编辑模式，长按快捷文本或分组即可拖动排序。也可以稍后在设置的操作反馈中开启。") },
        confirmButton = { TextButton(onClick = dialogFeedbackClick { preference.choose(true) }) { Text("开启") } },
        dismissButton = { TextButton(onClick = dialogFeedbackClick { preference.choose(false) }) { Text("暂不开启") } })
}
