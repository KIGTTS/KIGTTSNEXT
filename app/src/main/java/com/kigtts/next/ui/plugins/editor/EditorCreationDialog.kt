@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kigtts.next.ui.plugins.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.i18n.uiText
import com.kigtts.next.plugins.editor.validEditorPackageName
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun EditorCreationDialog(model: AppViewModel, close: () -> Unit) {
    var native by rememberSaveable { mutableStateOf(false) }
    var kotlin by rememberSaveable { mutableStateOf(false) }
    var preset by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var packageName by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val workspace = model.editorWorkspace
    val selectedPreset = if (preset) workspace.presets.byId(if (native) "native-catalog" else "lua-catalog") else null
    val scope = rememberCoroutineScope()
    val suggested = uiText("editor.preset")
    MotionAlertDialog(onDismissRequest = { if (!busy) close() }, title = { Text(uiText("editor.create")) }, text = {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EditorSegments(listOf("Lua", uiText("editor.native")), if (native) 1 else 0) {
                native = it == 1
                if (preset) name = ""
            }
            EditorSegments(listOf(uiText("editor.empty"), suggested), if (preset) 1 else 0) {
                preset = it == 1
            }
            if (native && !preset) EditorSegments(listOf("Java", "Kotlin"), if (kotlin) 1 else 0) { kotlin = it == 1 }
            selectedPreset?.let {
                Text(uiText("editor.preset_version", "version" to it.version, "api" to it.apiVersion.toString()),
                    style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(name, { name = it.take(64); error = false }, label = { Text(uiText("editor.name")) },
                singleLine = true, modifier = Modifier.fillMaxWidth().testTag("editor-create-name"))
            OutlinedTextField(packageName, { packageName = it.take(80); error = false },
                label = { Text(uiText("editor.package_name")) }, placeholder = { Text("com.example.myplugin") },
                singleLine = true, modifier = Modifier.fillMaxWidth().testTag("editor-create-package"))
            if (error) Text(uiText("editor.create_failed"), color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = {
        MotionTextButton(onClick = feedbackClick {
            if (!busy) scope.launch {
                busy = true
                try {
                    val language = if (!native) "lua" else if (kotlin && !preset) "kotlin" else "java"
                    workspace.create(model, language, selectedPreset?.id, name.trim().ifBlank { selectedPreset?.name.orEmpty() }, packageName.trim(), "")
                    close()
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { error = true }
                finally { busy = false }
            }
        }, enabled = !busy && validEditorPackageName(packageName.trim()) && (name.isNotBlank() || selectedPreset != null),
            modifier = Modifier.testTag("editor-create-confirm")) { Text(uiText("editor.create")) }
    }, dismissButton = {
        MotionTextButton(onClick = dialogFeedbackClick { if (!busy) close() }) { Text(uiText("action.cancel")) }
    })
}

@Composable
internal fun EditorSegments(labels: List<String>, selected: Int, select: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            SegmentedButton(selected == index, feedbackClick { select(index) },
                SegmentedButtonDefaults.itemShape(index, labels.size), label = { Text(label) })
        }
    }
}
