package com.kigtts.next.ui.plugins

import androidx.compose.runtime.*
import com.kigtts.next.state.AppViewModel
import kotlinx.coroutines.*

@Composable
internal fun LocalPackageInstaller(model: AppViewModel, candidate: PendingPackage, dismiss: () -> Unit) {
    var info by remember(candidate) { mutableStateOf<PackageInstallerInfo?>(null) }
    var error by remember(candidate) { mutableStateOf<String?>(null) }
    var busy by remember(candidate) { mutableStateOf(false) }
    var completed by remember(candidate) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(candidate) {
        try { info = withContext(Dispatchers.IO) { installerInfo(candidate,
            model.pluginManager.packages.installed().firstOrNull { it.manifest.id == candidate.preview.manifest.id }?.manifest) } }
        catch (cancel: CancellationException) { throw cancel }
        catch (failure: Exception) { error = failure.message ?: "安装信息无法读取" }
    }
    PackageInstallerDialog(info?.let(::listOf), busy, completed, error, install = {
        scope.launch {
            busy = true; error = null
            try {
                model.stopSpeech()
                model.pluginManager.install(candidate.file, candidate.preview.publisherSha256)
                model.refreshPlugins(); completed = true
            } catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { error = failure.message ?: "插件安装失败" }
            finally { busy = false }
        }
    }, dismiss = dismiss)
}
