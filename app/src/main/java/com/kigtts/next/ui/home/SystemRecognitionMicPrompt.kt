package com.kigtts.next.ui.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.i18n.uiText
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.next.ui.components.dialogFeedbackClick

@Composable
internal fun SystemRecognitionMicPrompt(model: AppViewModel) {
    val issue by model.systemRecognitionPermission.collectAsStateWithLifecycle()
    val request = issue ?: return
    val context = LocalContext.current
    val fallback = uiText("listening.mic_settings_failed")
    val dismiss = { model.systemRecognitionPermission.value = null }
    MotionAlertDialog(onDismissRequest = dismiss, title = { Text(uiText("listening.mic_title")) }, text = {
        Text(uiText("listening.mic_guide", "app" to (request.appName ?: uiText("listening.system_engine"))))
    }, confirmButton = {
        TextButton(dialogFeedbackClick {
            dismiss()
            val target = request.appPackage?.let { Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", it, null)) }
                ?: Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
            runCatching { context.startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { model.message(fallback) }
        }) { Text(uiText("listening.mic_open_settings")) }
    }, dismissButton = { TextButton(dialogFeedbackClick(dismiss)) { Text(uiText("listening.mic_later")) } })
}
