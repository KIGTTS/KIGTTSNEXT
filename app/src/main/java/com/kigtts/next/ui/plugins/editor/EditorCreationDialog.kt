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
import com.kigtts.next.ui.settings.SettingItem
import com.kigtts.next.ui.settings.SettingPosition
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
    val templateName = selectedPreset?.let { uiText("editor.preset_name_${it.language}") }.orEmpty()
    val scope = rememberCoroutineScope()
    val suggested = uiText("editor.preset")
    MotionAlertDialog(onDismissRequest = { if (!busy) close() }, dismissEnabled = !busy, title = { Text(uiText("editor.create")) }, text = {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val pluginType = uiText("editor.project_type")
            val nativeLabel = uiText("editor.native")
            SettingItem(pluginType, "", MaterialSymbols.vector("extension"), SettingPosition.FIRST) {
                ChoiceButtonGroup(pluginType, listOf(false, true), native, { if (it) nativeLabel else "Lua" }, {
                    native = it
                    if (preset) name = ""
                }, enabled = !busy, compactLabels = true)
            }
            val template = uiText("editor.project_template")
            val blank = uiText("editor.empty")
            SettingItem(template, "", MaterialSymbols.vector("dashboard_customize"), SettingPosition.MIDDLE) {
                ChoiceButtonGroup(template, listOf(false, true), preset, { if (it) suggested else blank }, { preset = it },
                    enabled = !busy, compactLabels = true)
                selectedPreset?.let {
                    Text(uiText("editor.preset_version", "version" to it.version, "api" to it.apiVersion.toString()),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (native && !preset) {
                val sourceLanguage = uiText("editor.source_language")
                SettingItem(sourceLanguage, "", MaterialSymbols.vector("code"), SettingPosition.MIDDLE) {
                    ChoiceButtonGroup(sourceLanguage, listOf(false, true), kotlin, { if (it) "Kotlin" else "Java" }, { kotlin = it },
                        enabled = !busy, compactLabels = true)
                }
            }
            SettingItem(uiText("editor.package_details"), "", MaterialSymbols.vector("inventory_2"), SettingPosition.LAST) {
                OutlinedTextField(name, { name = it.take(64); error = false }, label = { Text(uiText("editor.name")) },
                    singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("editor-create-name"))
                OutlinedTextField(packageName, { packageName = it.take(80); error = false }, enabled = !busy,
                    label = { Text(uiText("editor.package_name")) }, placeholder = { Text("com.example.myplugin") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().testTag("editor-create-package"))
                if (error) Text(uiText("editor.create_failed"), color = MaterialTheme.colorScheme.error)
            }
        }
    }, confirmButton = {
        MotionTextButton(onClick = feedbackClick {
            if (!busy) scope.launch {
                busy = true
                try {
                    val language = if (!native) "lua" else if (kotlin && !preset) "kotlin" else "java"
                    workspace.create(model, language, selectedPreset?.id, name.trim().ifBlank { templateName }, packageName.trim(), "")
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
