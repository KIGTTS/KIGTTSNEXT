package com.kigtts.next.ui.plugins

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.audio.MissingPiperComponent
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.components.*

@Composable
internal fun LegacyPiperPrompt(model: AppViewModel) {
    val guidance = model.legacyPiperGuidance
    val prompt by guidance.prompt.collectAsStateWithLifecycle()
    LegacyPiperPrompt(prompt, guidance::proceed, guidance::dismiss)
}

@Composable
internal fun LegacyPiperPrompt(missing: Set<MissingPiperComponent>?, install: (Boolean) -> Unit, dismiss: (Boolean) -> Unit) {
    if (missing.isNullOrEmpty()) return
    var neverRemind by remember { mutableStateOf(false) }
    MotionAlertDialog(onDismissRequest = { dismiss(neverRemind) }, title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(uiText("plugin.use_piper"), Modifier.weight(1f)); Icon(Icons.Default.RecordVoiceOver, null)
        }
    }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(uiText("plugin.piper_saved"))
            missing.sortedBy { it.ordinal }.forEach { Text("• ${it.label}") }
            Row(Modifier.fillMaxWidth().testTag("piper-never-remind").toggleable(neverRemind, role = Role.Checkbox,
                onValueChange = { neverRemind = it }), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(neverRemind, onCheckedChange = null)
                Text(uiText("plugin.no_reminder"), Modifier.padding(start = 8.dp))
            }
        }
    }, confirmButton = { MotionTextButton(dialogFeedbackClick { install(neverRemind) }) { Text(uiText("plugin.go_install")) } },
        dismissButton = { MotionTextButton(dialogFeedbackClick { dismiss(neverRemind) }) { Text(uiText("overlay.later")) } })
}
