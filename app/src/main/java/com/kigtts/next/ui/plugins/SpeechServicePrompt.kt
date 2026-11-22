package com.kigtts.next.ui.plugins

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.audio.SpeechServiceStatus
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.components.*

@Composable
internal fun SpeechServicePrompt(model: AppViewModel) {
    val guidance = model.synthesisGuidance
    val prompt by guidance.prompt.collectAsStateWithLifecycle()
    SpeechServicePrompt(prompt, guidance::proceed, guidance::later, guidance::hide)
}

@Composable
internal fun SpeechServicePrompt(status: SpeechServiceStatus?, proceed: () -> Unit, later: () -> Unit, hide: () -> Unit) {
    if (status == null) return
    val missing = status == SpeechServiceStatus.MISSING
    MotionAlertDialog(onDismissRequest = later,
        title = { Row(horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (missing) uiText("reading.install_prompt") else uiText("reading.enable_prompt"), Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
        } },
        text = { Text(if (missing) uiText("reading.offer")
            else uiText("reading.enable")) },
        confirmButton = { MotionTextButton(onClick = dialogFeedbackClick(proceed)) { Text(if (missing) uiText("plugin.go_install") else uiText("reading.manage")) } },
        dismissButton = { Column {
            MotionTextButton(onClick = dialogFeedbackClick(later)) { Text(uiText("overlay.later")) }
            if (missing) MotionTextButton(onClick = dialogFeedbackClick(hide)) { Text(uiText("reading.hide")) }
        } })
}
