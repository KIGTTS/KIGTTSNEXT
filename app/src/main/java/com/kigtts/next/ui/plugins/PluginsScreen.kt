@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.plugins

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.*
import kotlinx.coroutines.launch

@Composable
fun PluginsScreen(model: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<PendingPackage?>(null) }
    var remove by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
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
    com.kigtts.next.ui.management.ManagedItemList(ManagementList("plugins", rows, "plugins-order"), busy,
        invoke = { action, inputs ->
            val pieces = action.split('/')
            when (pieces[0]) {
                "plugins-order" -> model.reorderPlugins(PluginJson.decodeFromString(requireNotNull(inputs["order"])))
                "enable" -> model.changePlugin(pieces[1], inputs["checked"].toBoolean())
                "tts" -> model.selectEngine(pieces[1], true)
                "asr" -> model.selectEngine(pieces[1], false)
                "remove" -> remove = pieces[1]
                "page" -> model.navigate(Destination.PluginPage(pieces[1], pieces.drop(2).joinToString("/")))
            }
        }) {
        Text("插件", style = MaterialTheme.typography.headlineMedium)
        Text("拖动手柄或长按排序。条目菜单提供页面、引擎选择和卸载操作。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton(shapes = expressivePillShapes(), enabled = !busy, onClick = feedbackClick { importer.launch(arrayOf("*/*")) }) { Text("安装 .kigplugin") }
        if (model.pluginManager.status.isEmpty()) Text("尚未安装语音引擎插件")
    }
    pending?.let { candidate ->
        val manifest = candidate.preview.manifest
        AlertDialog(onDismissRequest = { candidate.file.delete(); pending = null }, title = { Text("安装 ${manifest.name}？") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("版本 ${manifest.version} · ${manifest.license}")
                Text("能力：${manifest.capabilities.joinToString("、")}")
                Text("发布者 SHA-256：${candidate.preview.publisherSha256}", style = MaterialTheme.typography.bodySmall)
                Text("仅安装可信来源的插件。插件在独立进程中运行，具有本应用的权限。")
            } }, confirmButton = { TextButton(enabled = !busy, onClick = feedbackClick {
                scope.launch {
                    busy = true
                    try {
                        model.stopSpeech(); model.pluginManager.install(candidate.file, candidate.preview.publisherSha256); model.refreshPlugins()
                        model.message("插件已安装")
                    } catch (error: Exception) { model.message(error.message ?: "插件安装失败") }
                    finally { candidate.file.delete(); pending = null; busy = false }
                }
            }) { Text("安装") } }, dismissButton = { TextButton(onClick = feedbackClick { candidate.file.delete(); pending = null }) { Text("取消") } })
    }
    remove?.let { id -> AlertDialog(onDismissRequest = { remove = null }, title = { Text("卸载插件？") },
        text = { Text("该插件的模型、下载、配置和临时文件也会删除。") },
        confirmButton = { TextButton(onClick = feedbackClick { model.changePlugin(id); remove = null }) { Text("卸载") } },
        dismissButton = { TextButton(onClick = feedbackClick { remove = null }) { Text("取消") } }) }
}
