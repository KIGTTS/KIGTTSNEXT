package com.kigtts.next.ui.plugins.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kigtts.next.i18n.uiText
import com.kigtts.next.plugins.editor.EditorSigningKey
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.settings.*
import kotlinx.coroutines.*

private sealed interface SigningRequest {
    data object Generate : SigningRequest
    data class Import(val uri: Uri) : SigningRequest
    data class Export(val key: EditorSigningKey) : SigningRequest
}

@Composable
internal fun EditorSigningScreen(model: AppViewModel) {
    val workspace = model.editorWorkspace
    val scope = rememberCoroutineScope()
    val genericFailure = uiText("editor.sign_failed")
    val done = uiText("editor.signature_saved")
    var request by remember { mutableStateOf<SigningRequest?>(null) }
    var keys by remember { mutableStateOf(emptyList<EditorSigningKey>()) }
    var busy by remember { mutableStateOf(true) }
    var requestFailed by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<Pair<EditorSigningKey, CharArray>?>(null) }
    suspend fun refresh() { keys = workspace.keys() }
    LaunchedEffect(workspace) {
        try { refresh() } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { model.message(genericFailure) }
        finally { busy = false }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) request = SigningRequest.Import(uri)
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pending = pendingExport
        pendingExport = null
        if (pending != null) {
            if (uri == null) pending.second.fill('\u0000')
            else scope.launch {
                busy = true
                try { workspace.exportSignature(uri, pending.first.fingerprint, pending.second); model.message(done) }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { model.message(genericFailure) }
                finally { busy = false; pending.second.fill('\u0000') }
            }
        }
    }
    DisposableEffect(Unit) { onDispose { pendingExport?.second?.fill('\u0000') } }
    SettingsLayout {
        SettingsGroup(uiText("editor.signatures")) {
            SettingItem(uiText("editor.new_signature"), "", Icons.Default.Add, SettingPosition.FIRST,
                enabled = !busy, onClick = { request = SigningRequest.Generate },
                modifier = Modifier.testTag("editor-new-signature"))
            SettingItem(uiText("editor.import_signature"), uiText("editor.signature_formats"), Icons.Default.Upload,
                SettingPosition.LAST, enabled = !busy, onClick = { import.launch(arrayOf("*/*")) },
                modifier = Modifier.testTag("editor-import-signature"))
        }
        if (keys.isNotEmpty()) SettingsGroup(uiText("editor.available_signatures")) {
            keys.forEachIndexed { index, key ->
                SettingItem(key.name, key.fingerprint, Icons.Default.Key,
                    com.kigtts.next.ui.plugins.settingPosition(index, keys.size), enabled = !busy,
                    onClick = { request = SigningRequest.Export(key) }, trailing = {
                        Icon(Icons.Default.Download, uiText("editor.export_signature"), Modifier.size(21.dp))
                    }, modifier = Modifier.testTag("editor-signature/${key.fingerprint}"))
            }
        }
    }
    request?.let { current ->
        SignatureRequestDialog(current, busy, requestFailed, {
            if (!busy) { request = null; requestFailed = false }
        }) { name, password ->
            requestFailed = false
            if (current is SigningRequest.Export) {
                pendingExport = current.key to password
                request = null
                export.launch("kigtts-${current.key.fingerprint.take(12)}.kigsign")
            } else scope.launch {
                busy = true
                try {
                    when (current) {
                        SigningRequest.Generate -> withContext(Dispatchers.IO) { workspace.signatures.generate(name) }
                        is SigningRequest.Import -> workspace.importSignature(current.uri, name, password)
                        else -> Unit
                    }
                    refresh()
                    request = null
                    model.message(done)
                } catch (cancel: CancellationException) { throw cancel }
                catch (failure: Exception) {
                    android.util.Log.w("KigttsEditorSigning", "Signature operation failed (${failure.javaClass.simpleName})")
                    requestFailed = true
                }
                finally { busy = false; password.fill('\u0000') }
            }
        }
    }
}

@Composable
private fun SignatureRequestDialog(request: SigningRequest, busy: Boolean, failed: Boolean, close: () -> Unit,
    submit: (String, CharArray) -> Unit) {
    var name by remember(request) { mutableStateOf("") }
    var password by remember(request) { mutableStateOf("") }
    val export = request is SigningRequest.Export
    val generate = request == SigningRequest.Generate
    val title = if (export) "editor.export_signature" else if (generate) "editor.new_signature" else "editor.import_signature"
    MotionAlertDialog(onDismissRequest = close, title = { Text(uiText(title)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (export) Text(uiText("editor.signature_export_info"))
            else OutlinedTextField(name, { name = it.take(64) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("editor-signature-name"),
                label = { Text(uiText("editor.signature_name")) }, enabled = !busy)
            if (!generate) OutlinedTextField(password, { password = it.take(128) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("editor-signature-password"),
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                label = { Text(uiText("editor.signature_password")) }, enabled = !busy)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (failed) Text(uiText("editor.sign_failed"), color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = {
        MotionTextButton(onClick = feedbackClick { submit(name.trim(), password.toCharArray()); password = "" },
            enabled = !busy && (export && password.length >= 8 || !export && name.isNotBlank()),
            modifier = Modifier.testTag("editor-signature-confirm")) { Text(uiText("action.save")) }
    }, dismissButton = { MotionTextButton(onClick = dialogFeedbackClick(close), enabled = !busy) { Text(uiText("action.cancel")) } })
}
