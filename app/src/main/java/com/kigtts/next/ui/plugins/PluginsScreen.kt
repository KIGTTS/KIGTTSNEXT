@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.plugins

import com.kigtts.next.i18n.uiText

import com.kigtts.next.ui.components.MotionTextButton

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.next.ui.components.dialogFeedbackClick
import com.kigtts.next.ui.components.rememberDialogClose
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.*
import kotlinx.coroutines.launch
import com.kigtts.next.ui.navigation.RegisterPageActions
import com.kigtts.next.ui.navigation.ToolbarAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PluginsScreen(model: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<PendingPackage?>(null) }
    var remove by remember { mutableStateOf<String?>(null) }
    var managedRemove by remember { mutableStateOf<Pair<String, String>?>(null) }
    var permission by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var about by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val state by model.state.collectAsStateWithLifecycle()
    val working = busy || state.busyPlugins.isNotEmpty()
    val recovery by model.safeModeSession.state.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose { pending?.file?.delete() } }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try { pending = inspectPackage(context, model, uri) }
            catch (error: Exception) { model.message(error.message ?: "插件包无法读取") }
            finally { busy = false }
        }
    }
    val rows = pluginManagementItems(model)
    RegisterPageActions(listOf(
        ToolbarAction("install-plugin",uiText("plugin.install"),Icons.Default.Extension,!working,preserveColorWhenLocked=true) { importer.launch(arrayOf("*/*")) },
        ToolbarAction("refresh-plugins",uiText("plugin.refresh"),Icons.Default.Refresh,!working,preserveColorWhenLocked=true) { scope.launch { busy=true;try { model.refreshPlugins() } finally { busy=false } } }
    ))
    com.kigtts.next.ui.management.ManagedItemList(ManagementList("plugins", rows, "plugins-order", emptyText = uiText("plugin.empty")), working, compactActions = true,
        invoke = { action, inputs ->
            val pieces = action.split('/')
            when (pieces[0]) {
                "plugins-order" -> model.reorderPlugins(PluginJson.decodeFromString(requireNotNull(inputs["order"])))
                "enable" -> model.changePlugin(pieces[1], inputs["checked"].toBoolean())
                "tts" -> model.selectEngine(pieces[1], true)
                "asr" -> model.selectEngine(pieces[1], false)
                "remove" -> remove = pieces[1]
                "page" -> model.navigate(Destination.PluginPage(pieces[1], pieces.drop(2).joinToString("/")))
                "permissions" -> permission = pieces[1] to pieces.getOrNull(2)
                "about" -> about = pieces[1]
                "managed" -> model.invoke(pieces[1], pieces.drop(2).joinToString("/"), inputs)
                "managed-remove" -> managedRemove = pieces[1] to pieces[2]
            }
        }) {
        if (recovery.active) {
            Text(uiText("plugin.safe"), color = MaterialTheme.colorScheme.error)
            MotionTextButton(enabled = !working, onClick = feedbackClick { scope.launch { busy = true
                try { model.safeModeSession.restorePlugins() } finally { busy = false }
            } }) { Text(uiText("recovery.restore")) }
        }
    }
    permission?.let { (id, project) -> PluginPermissionSettings(model, id, project) { permission = null } }
    about?.let { id -> InstalledPluginAbout(model, id) { about = null } }
    managedRemove?.let { (runtime, id) -> MotionAlertDialog(onDismissRequest = { managedRemove = null }, title = { Text(uiText("plugin.uninstall.title")) }, text = { Text(uiText("plugin.uninstall.lua")) },
        confirmButton = { MotionTextButton(onClick = dialogFeedbackClick {
            model.registry.plugin(runtime)?.contributions?.managedPackages?.firstOrNull { it.id == id }?.removeAction?.let { action -> model.invoke(runtime, action) }
            model.pluginManager.permissions.clear(model.pluginManager.permissionOwner(runtime, id)); managedRemove = null
        }) { Text(uiText("plugin.uninstall")) } }, dismissButton = { MotionTextButton(onClick = dialogFeedbackClick { managedRemove = null }) { Text(uiText("action.cancel")) } }) }
    pending?.let { candidate ->
        LocalPackageInstaller(model, candidate) { candidate.file.delete(); pending = null }
    }
    remove?.let { id -> MotionAlertDialog(onDismissRequest = { remove = null }, title = { Text(uiText("plugin.uninstall.title")) },
        text = { Text(uiText("plugin.uninstall.native")) },
        confirmButton = { MotionTextButton(onClick = dialogFeedbackClick { model.changePlugin(id); remove = null }) { Text(uiText("plugin.uninstall")) } },
        dismissButton = { MotionTextButton(onClick = dialogFeedbackClick { remove = null }) { Text(uiText("action.cancel")) } }) }
}
