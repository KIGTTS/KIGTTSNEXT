package com.kigtts.next.ui.plugins

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.kigtts.next.i18n.uiText
import com.kigtts.next.theme.SavedAppearanceRequest
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.next.ui.components.MotionTextButton
import com.kigtts.next.ui.components.dialogFeedbackClick

@Composable
internal fun PluginAppearanceConfirmation(request: SavedAppearanceRequest, answer: (Boolean) -> Unit) {
    val plugin = request.plugin.descriptor.name
    MotionAlertDialog(
        onDismissRequest = { answer(false) },
        title = { Text(uiText("plugin.appearance.save_title")) },
        text = { Text(uiText("plugin.appearance.save_body", "plugin" to plugin)) },
        dismissButton = { MotionTextButton(dialogFeedbackClick { answer(false) }) { Text(uiText("action.cancel")) } },
        confirmButton = { MotionTextButton(dialogFeedbackClick { answer(true) }) { Text(uiText("action.save")) } },
    )
}
