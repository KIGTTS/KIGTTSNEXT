@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.plugins

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
    var busy by remember { mutableStateOf(false) }
    val state by model.state.collectAsStateWithLifecycle()
    val working = busy || state.busyPlugins.isNotEmpty()
    val recovery = remember { com.kigtts.next.recovery.CrashRecovery(context) }
    var safeMode by remember { mutableStateOf(recovery.safeMode()) }
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
        ToolbarAction("install-plugin","安装插件",Icons.Default.Extension,!working,preserveColorWhenLocked=true) { importer.launch(arrayOf("*/*")) },
        ToolbarAction("refresh-plugins","刷新插件",Icons.Default.Refresh,!working,preserveColorWhenLocked=true) { scope.launch { busy=true;try { model.refreshPlugins() } finally { busy=false } } }
    ))
    com.kigtts.next.ui.management.ManagedItemList(ManagementList("plugins", rows, "plugins-order"), working,
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
                "managed" -> model.invoke(pieces[1], pieces.drop(2).joinToString("/"), inputs)
                "managed-remove" -> managedRemove = pieces[1] to pieces[2]
            }
        }) {
        if (safeMode) {
            Text("软件多次启动失败，已进入安全模式并暂时停用插件。插件数据和原有设置已保留。", color = MaterialTheme.colorScheme.error)
            MotionTextButton(enabled = !working, onClick = feedbackClick { scope.launch { busy = true
                try { recovery.restore(); safeMode = false; model.refreshPlugins() } finally { busy = false }
            } }) { Text("恢复插件") }
        }
        Text("管理已安装的插件", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (model.pluginManager.status.isEmpty()) Text("还没有安装插件")
    }
    permission?.let { (id, project) -> PluginPermissionSettings(model, id, project) { permission = null } }
    managedRemove?.let { (runtime, id) -> MotionAlertDialog(onDismissRequest = { managedRemove = null }, title = { Text("卸载插件？") }, text = { Text("将删除这个 Lua 插件及其保存的数据。") },
        confirmButton = { MotionTextButton(onClick = dialogFeedbackClick {
            model.registry.plugin(runtime)?.contributions?.managedPackages?.firstOrNull { it.id == id }?.removeAction?.let { action -> model.invoke(runtime, action) }
            model.pluginManager.permissions.clear(model.pluginManager.permissionOwner(runtime, id)); managedRemove = null
        }) { Text("卸载") } }, dismissButton = { MotionTextButton(onClick = dialogFeedbackClick { managedRemove = null }) { Text("取消") } }) }
    pending?.let { candidate ->
        LocalPackageInstaller(model, candidate) { candidate.file.delete(); pending = null }
    }
    remove?.let { id -> MotionAlertDialog(onDismissRequest = { remove = null }, title = { Text("卸载插件？") },
        text = { Text("将删除这个插件及其保存的数据，包括模型、下载文件和设置。") },
        confirmButton = { MotionTextButton(onClick = dialogFeedbackClick { model.changePlugin(id); remove = null }) { Text("卸载") } },
        dismissButton = { MotionTextButton(onClick = dialogFeedbackClick { remove = null }) { Text("取消") } }) }
}
