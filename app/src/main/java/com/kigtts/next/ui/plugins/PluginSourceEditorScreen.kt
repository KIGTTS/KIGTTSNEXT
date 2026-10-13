package com.kigtts.next.ui.plugins

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.media.code.*
import com.kigtts.next.ui.navigation.*
import com.kigtts.plugin.api.PageBlock
import kotlinx.coroutines.CancellationException

/** Same editor, line numbers and keyboard toolbar as HTML, independent of the plugin's language/runtime. */
@Composable
internal fun PluginSourceEditorScreen(block: PageBlock.SourceEditor, enabled: Boolean,
    invoke: (String, Map<String, String>) -> Unit) {
    val editor = remember(block.id) { CodeEditorState(block.text, maxCharacters = 64_000, maxBytes = 192_000) }
    val focus = remember { FocusRequester() }
    var baseline by remember(block.id) { mutableStateOf(block.text) }
    val dirty = editor.value.text != baseline
    var discard by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(block.text) {
        if (block.text == editor.value.text) baseline = block.text
        else if (!dirty) { editor.change(androidx.compose.ui.text.input.TextFieldValue(block.text)); baseline = block.text }
        else error = "文件已在其它位置更新，请先保存或放弃当前修改"
    }
    val interactive = enabled && LocalPageActive.current && !block.readOnly
    val leave = LocalPageBackAction.current
    RegisterPageBackGuard(LocalPageActive.current && dirty) { discard = true }
    RegisterPageActions(listOf(ToolbarAction("source-save/" + block.id, "保存文件", Icons.Default.Save,
        interactive && dirty) { if (editor.value.text.length <= 64_000) invoke(block.saveAction, mapOf("value" to editor.value.text))
            else error = "文件内容过长" }))
    PredictiveBackHandler(LocalPageActive.current && dirty && !discard) { events ->
        try { events.collect { }; discard = true } catch (_: CancellationException) { }
    }
    Column(Modifier.fillMaxSize()) {
        if (error != null) Text(error.orEmpty(), Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
        CodeTextArea(editor, interactive, focus, Modifier.weight(1f).fillMaxWidth()) { error = it }
        CodeShortcutBar(editor, interactive, { focus.requestFocus() }) { key ->
            try { editor.invoke(key); focus.requestFocus() } catch (failure: IllegalArgumentException) { error = failure.message }
        }
    }
    if (discard) MotionAlertDialog(onDismissRequest = { discard = false }, title = { Text("放弃未保存的修改？") },
        confirmButton = { MotionTextButton(dialogFeedbackClick {
            editor.change(androidx.compose.ui.text.input.TextFieldValue(baseline)); discard = false; leave?.invoke()
        }) { Text("放弃") } }, dismissButton = { MotionTextButton(dialogFeedbackClick { discard = false }) { Text("继续编辑") } })
}
