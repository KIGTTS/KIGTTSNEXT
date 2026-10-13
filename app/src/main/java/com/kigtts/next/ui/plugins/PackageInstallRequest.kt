package com.kigtts.next.ui.plugins

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

/** A store can request installation, but only native host review grants publisher trust/version replacement. */
@Composable
internal fun PackageInstallRequest(model: AppViewModel, request: Pair<String, PluginOutcome.InstallPackage>?, dismiss: () -> Unit) {
    if (request == null) return
    val (owner, outcome) = request
    var candidates by remember(request) { mutableStateOf<List<PendingPackage>?>(null) }
    var error by remember(request) { mutableStateOf<String?>(null) }
    var busy by remember(request) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(request) {
        val copied = mutableListOf<PendingPackage>()
        try {
            withContext(Dispatchers.IO) {
                require(outcome.dependencies.size <= 32) { "依赖包过多" }
                (outcome.dependencies + outcome.resource).forEach { resource ->
                    require(resource.pluginId == null || resource.pluginId == owner) { "安装资源不属于当前插件" }
                    val source = model.pluginManager.paths.storage(owner).file(resource.path)
                    require(source.isFile && source.length() in 1..768L * 1024 * 1024) { "插件包不存在或过大" }
                    val frozen = File(model.pluginManager.paths.packages, ".incoming-${UUID.randomUUID()}.kigplugin")
                    try { source.copyTo(frozen); copied += PendingPackage(frozen, model.pluginManager.packages.inspect(frozen)) }
                    catch (failure: Exception) { frozen.delete(); throw failure }
                }
                require(copied.map { it.preview.manifest.id }.distinct().size == copied.size) { "安装计划包含重复插件" }
                val installed = model.pluginManager.packages.installed().associateBy { it.manifest.id }
                copied.forEach { candidate -> installed[candidate.preview.manifest.id]?.let { old ->
                    require(old.publisherSha256 == candidate.preview.publisherSha256) { "更新包发布者与原安装不一致" }
                    require(candidate.preview.manifest.versionCode > old.manifest.versionCode || outcome.allowDowngrade && candidate.preview.manifest.versionCode != old.manifest.versionCode) { "版本已安装或需要明确确认切换旧版" }
                } }
            }
            candidates = copied.toList()
        } catch (cancel: CancellationException) { copied.forEach { it.file.delete() }; throw cancel }
        catch (failure: Exception) { copied.forEach { it.file.delete() }; error = failure.message ?: "安装计划无法读取" }
    }
    DisposableEffect(candidates) { val files = candidates.orEmpty().map { it.file }; onDispose { files.forEach(File::delete) } }
    MotionAlertDialog(onDismissRequest = dismiss, dismissEnabled = !busy,
        title = { Text(if (outcome.allowDowngrade) "确认切换插件版本？" else "确认安装插件？") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                else if (candidates == null) Text("正在检查安装计划…")
                else {
                    Text("将按依赖顺序安装 ${candidates!!.size} 个包。")
                    candidates!!.forEach { candidate -> val manifest = candidate.preview.manifest
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(manifest.name, style = MaterialTheme.typography.titleMedium)
                                Text("${manifest.version} · ${manifest.license}", style = MaterialTheme.typography.bodySmall)
                                Text("能力：${manifest.capabilities.joinToString("、")}", style = MaterialTheme.typography.bodySmall)
                                Text("发布者 ${candidate.preview.publisherSha256}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Text("插件拥有本应用的权限，请确认来源可信。", style = MaterialTheme.typography.bodySmall)
                    if (outcome.allowDowngrade) Text("旧版会替换现有程序；资源和设置保留，旧版可能无法读取新版数据。", style = MaterialTheme.typography.bodySmall)
                }
            }
        }, confirmButton = {
            val close = rememberDialogClose()
            MotionTextButton(enabled = candidates != null && !busy && error == null, onClick = feedbackClick {
                scope.launch {
                    busy = true
                    val completed = mutableListOf<String>()
                    try {
                        model.stopSpeech()
                        candidates!!.forEach { candidate ->
                            model.pluginManager.install(candidate.file, candidate.preview.publisherSha256, outcome.allowDowngrade)
                            completed += candidate.preview.manifest.name
                        }
                        model.refreshPlugins(); model.message("已安装 ${completed.size} 个插件")
                    } catch (failure: Exception) {
                        model.refreshPlugins()
                        model.message((failure.message ?: "安装未完成") + if (completed.isEmpty()) "" else "；已完成：${completed.joinToString("、")}")
                    } finally { busy = false; close(dismiss) }
                }
            }) { Text(if (outcome.allowDowngrade) "切换版本" else "安装") }
        }, dismissButton = { MotionTextButton(dialogFeedbackClick(dismiss), enabled = !busy) { Text("取消") } })
}
