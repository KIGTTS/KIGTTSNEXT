package com.kigtts.next.ui.plugins

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
            Text("继续使用 Piper 音色", Modifier.weight(1f)); Icon(Icons.Default.RecordVoiceOver, null)
        }
    }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("检测到已保存的 Piper 语音包。安装以下插件后即可继续使用朗读：")
            missing.sortedBy { it.ordinal }.forEach { Text("• ${it.label}") }
            Row(Modifier.fillMaxWidth().testTag("piper-never-remind").toggleable(neverRemind, role = Role.Checkbox,
                onValueChange = { neverRemind = it }), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(neverRemind, onCheckedChange = null)
                Text("下次不再提示", Modifier.padding(start = 8.dp))
            }
        }
    }, confirmButton = { MotionTextButton(dialogFeedbackClick { install(neverRemind) }) { Text("前往安装") } },
        dismissButton = { MotionTextButton(dialogFeedbackClick { dismiss(neverRemind) }) { Text("稍后再说") } })
}
