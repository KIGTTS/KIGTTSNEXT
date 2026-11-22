package com.kigtts.next.ui.plugins

import com.kigtts.next.i18n.uiText

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.media.code.*
import com.kigtts.next.ui.navigation.*
import com.kigtts.plugin.api.PageBlock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Same editor, line numbers and keyboard toolbar as HTML, independent of the plugin's language/runtime. */
@Composable
internal fun PluginSourceEditorScreen(block: PageBlock.SourceEditor, enabled: Boolean,
    invoke: (String, Map<String, String>) -> Unit) {
    val editor = rememberSaveable(block.id, saver = codeEditorStateSaver(64_000, 192_000)) {
        CodeEditorState(block.text, maxCharacters = 64_000, maxBytes = 192_000)
    }
    val focus = remember { FocusRequester() }
    var baseline by rememberSaveable(block.id) { mutableStateOf(block.text) }
    val dirty = editor.value.text != baseline
    var discard by rememberSaveable(block.id) { mutableStateOf(false) }
    var error by rememberSaveable(block.id) { mutableStateOf<String?>(null) }
    var requestedSave by rememberSaveable(block.id) { mutableStateOf<String?>(null) }
    var returnAfterSave by rememberSaveable(block.id) { mutableStateOf(false) }
    var observedBusy by rememberSaveable(block.id) { mutableStateOf(false) }
    var leaving by rememberSaveable(block.id) { mutableStateOf(false) }
    val currentEnabled by rememberUpdatedState(enabled)
    val active = LocalPageActive.current
    val currentActive = rememberUpdatedState(active)
    LaunchedEffect(block.text) {
        if (block.text == editor.value.text) baseline = block.text
        else if (!dirty) { editor.change(androidx.compose.ui.text.input.TextFieldValue(block.text)); baseline = block.text }
        else if (block.text != baseline && block.text != requestedSave)
            error = "文件已在其它位置更新，请先保存或放弃当前修改"
    }
    val saving = requestedSave != null || leaving
    val interactive = enabled && active && !block.readOnly && !saving
    val leave = LocalPageBackAction.current
    fun saveDraft(returnOnSuccess: Boolean = false) {
        if (!interactive || !dirty) return
        val value = editor.value.text
        if (value.length > 64_000 || value.toByteArray(Charsets.UTF_8).size > 192_000) {
            error = "文件内容过长"; return
        }
        requestedSave = value; returnAfterSave = returnOnSuccess; observedBusy = false; error = null
        try { invoke(block.saveAction, mapOf("value" to value)) }
        catch (failure: Exception) {
            requestedSave = null; returnAfterSave = false
            error = failure.message ?: "保存失败，请重试"
        }
    }
    LaunchedEffect(requestedSave, block.text, enabled) {
        val requested = requestedSave ?: return@LaunchedEffect
        if (!enabled) observedBusy = true
        if (enabled && block.text == requested) {
            baseline = requested
            requestedSave = null
            // An ACK may arrive while a child page covers this editor. Keep the accepted return
            // until the visible confirmation can perform its exit animation.
            leaving = returnAfterSave
        } else if (enabled && observedBusy) {
            requestedSave = null; returnAfterSave = false
            error = "保存结果尚未确认，请重试"
        }
    }
    LaunchedEffect(requestedSave) {
        val requested = requestedSave ?: return@LaunchedEffect
        delay(15_000)
        if (requestedSave == requested && currentEnabled) {
            requestedSave = null; returnAfterSave = false
            error = "保存结果尚未确认，请重试"
        }
    }
    RegisterPageBackGuard(active && (dirty || saving)) { if (!saving) discard = true }
    RegisterPageActions(listOf(ToolbarAction("source-save/" + block.id, uiText("file.save"), Icons.Default.Save,
        interactive && dirty) { saveDraft() }))
    RegisterPageLoading(!enabled || saving)
    PredictiveBackHandler(active && (dirty || saving) && !discard) { events ->
        try { events.collect { }; if (!saving) discard = true } catch (_: CancellationException) { }
    }
    Column(Modifier.fillMaxSize()) {
        if (error != null) Text(error.orEmpty(), Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
        CodeTextArea(editor, interactive, focus, Modifier.weight(1f).fillMaxWidth()) { error = it }
        CodeShortcutBar(editor, interactive, { focus.requestFocus() }) { key ->
            try { editor.invoke(key); focus.requestFocus() } catch (failure: IllegalArgumentException) { error = failure.message }
        }
    }
    if (active && discard) SaveChangesDialog(
        save = { _ -> saveDraft(returnOnSuccess = true) },
        discard = { editor.change(androidx.compose.ui.text.input.TextFieldValue(baseline)); discard = false; leave?.invoke() },
        keepEditing = { discard = false }, canSave = enabled && !block.readOnly && dirty,
        busy = saving || !enabled, error = error, completed = if (leaving) ({
            if (currentActive.value && leaving) { leaving = false; returnAfterSave = false; discard = false; leave?.invoke() }
        }) else null)
}
