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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("插件", style = MaterialTheme.typography.headlineMedium)
        Text("语音引擎通过独立插件安装。插件可注册按钮、菜单和页面，沿用应用主题。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton(shapes = expressivePillShapes(), enabled = !busy, onClick = feedbackClick { importer.launch(arrayOf("*/*")) }) { Text("安装 .kigplugin") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (model.pluginManager.status.isEmpty()) Text("尚未安装语音引擎插件")
        model.pluginManager.status.forEach { status ->
            val info = status.installed
            val id = info.manifest.id
            val plugin = model.registry.plugin(id)
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(info.manifest.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        Switch(info.enabled, onCheckedChange = feedbackChange { model.changePlugin(id, it) })
                    }
                    Text("v${info.manifest.version} · ${info.manifest.license}", style = MaterialTheme.typography.labelMedium)
                    status.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    plugin?.tts?.let { engine ->
                        if (engine.info.role == TtsRole.SERVICE || model.registry.plugins.none { it.tts?.info?.role == TtsRole.SERVICE })
                            EngineChoice("语音合成", model.selectedTtsId == id, engine.info.availability) { model.selectEngine(id, true) }
                        else Text("由语音合成服务统一选用声音")
                    }
                    plugin?.asr?.let { engine ->
                        Text(when (engine.info.streaming) { StreamingKind.SEGMENTED -> "完整录音 / 分段流式"; StreamingKind.INCREMENTAL -> "完整录音 / 增量流式"; StreamingKind.NONE -> "完整录音" })
                        EngineChoice("语音识别", model.selectedAsrId == id, engine.info.availability) { model.selectEngine(id, false) }
                    }
                    PluginPages(plugin, model)
                    TextButton(onClick = feedbackClick { remove = id }) { Text("卸载并删除插件数据") }
                }
            }
        }
        model.registry.plugins.filter { plugin -> model.pluginManager.status.none { it.installed.manifest.id == plugin.descriptor.id } }.forEach { plugin ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(plugin.descriptor.name, style = MaterialTheme.typography.titleLarge)
                    Text(plugin.descriptor.description)
                    Text("v${plugin.descriptor.version}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    PluginPages(plugin, model)
                }
            }
        }
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

@Composable private fun EngineChoice(label: String, selected: Boolean, available: EngineAvailability, select: () -> Unit) {
    if (available == EngineAvailability.NEEDS_RESOURCES) Text("需要先导入资源", color = MaterialTheme.colorScheme.onSurfaceVariant)
    FilledTonalButton(shapes = expressivePillShapes(), onClick = feedbackClick(select)) { Text(if (selected) "当前${label}引擎" else "用作${label}引擎") }
}
@Composable private fun PluginPages(plugin: KigPlugin?, model: AppViewModel) {
    plugin?.contributions?.drawerEntries?.forEach { entry ->
        FilledTonalButton(shapes = expressivePillShapes(), onClick = feedbackClick { model.navigate(Destination.PluginPage(plugin.descriptor.id, entry.pageId)) }) { Text("打开${entry.label}") }
    }
}
