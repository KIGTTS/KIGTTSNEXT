package com.kigtts.next.ui.plugins

import com.kigtts.next.i18n.uiText
import androidx.activity.compose.BackHandler

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.files.*
import com.kigtts.next.ui.navigation.LocalPageActive
import com.kigtts.next.ui.navigation.RegisterPageBackGuard
import com.kigtts.plugin.api.*

private data class PluginFileTransfer(val paths: List<String>, val copy: Boolean, val origin: String)

/** Declarative file browsing never reads plugin storage; each mutation goes through the owning plugin. */
@Composable
internal fun PluginFilesScreen(files: PageBlock.Files, enabled: Boolean, invoke: (String, Map<String, String>) -> Unit) {
    val foreground = LocalPageActive.current
    val active = enabled && foreground
    var selected by rememberSaveable(files.openAction) { mutableStateOf(emptyList<String>()) }
    var transfer by remember(files.openAction) { mutableStateOf<PluginFileTransfer?>(null) }
    var rename by remember(files.openAction) { mutableStateOf<BrowserFile?>(null) }
    var deleting by remember(files.openAction) { mutableStateOf(emptyList<String>()) }
    val folder = uiText("file.folder")
    val items = remember(files.items, folder) {
        files.items.map { file ->
            if (file.directory) BrowserFile(file.path, file.name, true, folder)
            else BrowserFile(file.path, file.name, false, sizeBytes = file.bytes)
        }
    }
    fun navigate(directory: String) {
        if (directory.isEmpty() && files.parentAction != null) invoke(requireNotNull(files.parentAction), mapOf("directory" to directory, "path" to directory))
        else invoke(files.openAction, mapOf("path" to directory, "directory" to directory))
    }
    fun operation(op: String, paths: List<String>, destination: String = "") = invoke(files.operationAction,
        mapOf("op" to op, "paths" to PluginJson.encodeToString(paths), "destination" to destination))
    fun chooseTransfer(paths: List<String>, copy: Boolean) { transfer = PluginFileTransfer(paths.toList(), copy, files.directory) }
    LaunchedEffect(files.directory) { selected = emptyList() }
    val interceptBack = foreground && transfer == null && rename == null && deleting.isEmpty() &&
        (selected.isNotEmpty() || files.directory.isNotEmpty())
    val backInFolder = {
        if (selected.isNotEmpty()) selected = emptyList()
        else if (active) navigate(files.directory.substringBeforeLast('/', ""))
    }
    RegisterPageBackGuard(interceptBack, backInFolder)
    BackHandler(interceptBack, onBack = backInFolder)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("plugin-files")) {
        DirectoryBreadcrumbs(files.directory, active, ::navigate)
        FileSelectionBar(selected, items.size, active,
            { selected = if (selected.size == items.size) emptyList() else items.map { it.path } }, { selected = emptyList() },
            { chooseTransfer(selected, false) }, { chooseTransfer(selected, true) }, { deleting = selected.toList() }) {
            files.importAction?.let { action -> MotionIconButton(feedbackClick { invoke(action, mapOf("directory" to files.directory)) }, enabled = active) {
                Icon(Icons.Default.FileOpen, uiText("file.import"))
            } }
            files.createAction?.let { action -> MotionIconButton(feedbackClick { invoke(action, mapOf("directory" to files.directory)) }, enabled = active) {
                Icon(Icons.Default.Add, uiText("accessibility.create_file_or_folder"))
            } }
        }
        key(files.directory) {
            FileBrowserRows(items, selected, active, { path -> selected = if (path in selected) selected - path else selected + path },
                { item -> if (item.directory) navigate(item.path) else invoke(files.openAction, mapOf("path" to item.path)) },
                { item, action -> when (action) {
                    FileAction.MOVE -> chooseTransfer(listOf(item.path), false)
                    FileAction.COPY -> chooseTransfer(listOf(item.path), true)
                    FileAction.RENAME -> rename = item
                    FileAction.DELETE -> deleting = listOf(item.path)
                } }, Modifier.weight(1f), tagPrefix = "plugin-file", emptyText = uiText("file.empty"))
        }
    }
    if (foreground) transfer?.let { request -> FileDestinationDialog(request.copy, files.directory,
        items, request.paths, active, ::navigate, { destination, finish ->
            finish(); operation(if (request.copy) "copy" else "move", request.paths, destination)
        }, { transfer = null; if (files.directory != request.origin) navigate(request.origin) },
        confirmedClose = { transfer = null; selected = emptyList() }) }
    if (foreground) rename?.let { item ->
        var name by remember(item.path) { mutableStateOf(item.name) }
        MotionAlertDialog(onDismissRequest = { rename = null }, title = { Text(uiText("common.rename")) }, text = {
            OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(uiText("common.name")) }, enabled = active)
        }, confirmButton = {
            val finish = rememberDialogClose()
            MotionTextButton(feedbackClick { finish { rename = null }; operation("rename", listOf(item.path), name) },
                enabled = active && validBrowserName(name)) { Text(uiText("common.confirm")) }
        }, dismissButton = { MotionTextButton(dialogFeedbackClick { rename = null }) { Text(uiText("action.cancel")) } })
    }
    if (foreground && deleting.isNotEmpty()) MotionAlertDialog(onDismissRequest = { deleting = emptyList() }, title = { Text(uiText("plugin.delete_selected_files")) },
        text = { Text(uiText("plugin.delete_items", "count" to deleting.size.toString())) }, confirmButton = {
            val finish = rememberDialogClose()
            MotionTextButton(feedbackClick { val paths = deleting; finish { deleting = emptyList(); selected = emptyList() }; operation("delete", paths) }, enabled = active) { Text(uiText("action.delete")) }
        }, dismissButton = { MotionTextButton(dialogFeedbackClick { deleting = emptyList() }) { Text(uiText("action.cancel")) } })
}

internal fun validBrowserName(name: String) = name.isNotBlank() && name.length <= 180 && name !in setOf(".", "..") &&
    name.none { it in "/\\:*?\"<>|" || it.code < 32 }
