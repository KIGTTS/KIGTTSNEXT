package com.kigtts.next.ui.plugins

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.next.ui.components.dialogFeedbackClick
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.plugin.api.PluginOutcome
import com.kigtts.plugin.api.PluginJson
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch

/** Native host UI mediates text/file selection and hardware permission requests. */
@Composable
fun PluginRequests(model: AppViewModel, input: Pair<String, PluginOutcome.TextInput>?,
    import: Pair<String, PluginOutcome.ImportResource>?, dismissInput: () -> Unit, dismissImport: () -> Unit,
    capture: Pair<String, PluginOutcome.CapturePhoto>? = null, dismissCapture: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var capturePlugin by rememberSaveable { mutableStateOf<String?>(null) }
    var captureJson by rememberSaveable { mutableStateOf<String?>(null) }
    val photos = com.kigtts.next.ui.cards.rememberCardPhotoSources({ selected ->
        val owner = capturePlugin; val json = captureJson; capturePlugin = null; captureJson = null
        if (owner == null || json == null) selected.release()
        else scope.launch {
            try {
                val request = PluginJson.decodeFromString<PluginOutcome.CapturePhoto>(json)
                importPluginResource(context, model, owner, PluginOutcome.ImportResource(request.title, request.directory, request.actionId, mimeTypes = listOf("image/*")), selected.uri)
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (error: Exception) { model.message(error.message ?: "拍摄照片未能添加") }
            finally { selected.release() }
        }
    }, { message -> capturePlugin = null; captureJson = null; model.message(message) },
        onCameraCancelled = { capturePlugin = null; captureJson = null })
    LaunchedEffect(capture) { capture?.let { (owner, request) ->
        capturePlugin = owner; captureJson = PluginJson.encodeToString(request); photos.camera(); dismissCapture()
    } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), model::microphoneResult)
    LaunchedEffect(model) { model.microphoneRequests.collect { permission.launch(Manifest.permission.RECORD_AUDIO) } }
    val overlay = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { model.overlayResult() }
    LaunchedEffect(model) { model.overlayRequests.collect {
        overlay.launch(android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:" + context.packageName)))
    } }
    var importing by remember { mutableStateOf<Pair<String, PluginOutcome.ImportResource>?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val request = importing; importing = null
        if (uri != null && request != null) scope.launch {
            try { importPluginResource(context, model, request.first, request.second, uri) }
            catch (error: Exception) { model.message(error.message ?: "资源导入失败") }
        }
    }
    val multiplePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val request = importing; importing = null
        if (uris.isNotEmpty() && request != null) scope.launch {
            try { importPluginResources(context, model, request.first, request.second, uris) }
            catch (error: Exception) { model.message(error.message ?: "资源导入失败") }
        }
    }
    LaunchedEffect(import) { import?.let { importing = it
        if (it.second.multiple) multiplePicker.launch(it.second.mimeTypes.toTypedArray()) else picker.launch(it.second.mimeTypes.toTypedArray())
        dismissImport()
    } }
    input?.let { (id, request) ->
        key(id, request.actionId) {
        val text = rememberTextFieldState(initialText = request.initialValue)
        MotionAlertDialog(onDismissRequest = dismissInput, title = { Text(request.title) },
            text = { Column(Modifier.fillMaxWidth()) { OutlinedTextField(state = text,
                modifier = Modifier.fillMaxWidth().testTag("plugin-input-value"),
                outputTransformation = if (request.secret) OutputTransformation { replace(0,length,"•".repeat(length)) } else null,
                lineLimits = if (request.multiline) TextFieldLineLimits.MultiLine(maxHeightInLines = 8) else TextFieldLineLimits.SingleLine) } },
            confirmButton = { TextButton(onClick = dialogFeedbackClick { dismissInput(); model.invoke(id, request.actionId, mapOf("value" to text.text.toString())) }) { Text("确定") } },
            dismissButton = { TextButton(onClick = dialogFeedbackClick(dismissInput)) { Text("取消") } })
        }
    }
}
