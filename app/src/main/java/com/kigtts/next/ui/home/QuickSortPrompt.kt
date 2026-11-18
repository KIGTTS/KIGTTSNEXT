package com.kigtts.next.ui.home

import com.kigtts.next.i18n.uiText

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
        title = { Text(uiText("quick.sort_enable")) },
        text = { Text(uiText("quick.sort_summary")) },
        confirmButton = { TextButton(onClick = dialogFeedbackClick { preference.choose(true) }) { Text(uiText("action.enable")) } },
        dismissButton = { TextButton(onClick = dialogFeedbackClick { preference.choose(false) }) { Text(uiText("quick.not_now")) } })
}
