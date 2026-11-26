package com.kigtts.next.ui.plugins.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.i18n.uiText
import com.kigtts.next.plugins.editor.EDITOR_SIGNING_PAGE
import com.kigtts.next.plugins.editor.EditorSigningKey
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.state.Destination
import com.kigtts.next.state.PLUGIN_EDITOR_ID
import com.kigtts.next.ui.components.*
import kotlinx.coroutines.CancellationException

@Composable
internal fun EditorPackageDialog(model: AppViewModel, owner: String, project: String, close: () -> Unit) {
    val workspace = model.editorWorkspace
    var author by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(workspace.signatures.selected(owner, project)) }
    var keys by remember { mutableStateOf<List<EditorSigningKey>?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val error = uiText("editor.sign_failed")
    LaunchedEffect(workspace, owner, project) {
        try { author = workspace.author(owner, project); keys = workspace.keys(); loaded = true }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { model.message(error) }
    }
    MotionAlertDialog(onDismissRequest = close, title = { Text(uiText("editor.package_details")) }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(uiText("editor.package_name"), style = MaterialTheme.typography.labelMedium)
            Text(project, Modifier.testTag("editor-package-name"))
            OutlinedTextField(author, { author = it.take(120) }, enabled = loaded, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("editor-author"),
                label = { Text(uiText("editor.author")) })
            Text(uiText("editor.signature"), style = MaterialTheme.typography.titleSmall)
            val options = listOf(null to uiText("editor.default_signature")) + keys.orEmpty().map { it.fingerprint to it.name }
            options.forEach { (hash, label) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(selected == hash, onClick = feedbackClick { selected = hash }, enabled = loaded)
                    Column {
                        TextButton(onClick = feedbackClick { selected = hash }, enabled = loaded) { Text(label) }
                        if (hash != null) Text(hash.take(12), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            MotionTextButton(onClick = feedbackClick {
                close()
                model.navigate(Destination.PluginPage(PLUGIN_EDITOR_ID, EDITOR_SIGNING_PAGE))
            }) { Text(uiText("editor.signatures")) }
        }
    }, confirmButton = {
        MotionTextButton(onClick = feedbackClick {
            try {
                workspace.signatures.select(owner, project, selected)
                model.invoke(owner, "editor-author/$project", mapOf("author" to author.trim()))
                close()
            } catch (_: Exception) { model.message(error) }
        }, enabled = loaded, modifier = Modifier.testTag("editor-save-package")) { Text(uiText("action.save")) }
    }, dismissButton = { MotionTextButton(onClick = dialogFeedbackClick(close)) { Text(uiText("action.cancel")) } })
}
