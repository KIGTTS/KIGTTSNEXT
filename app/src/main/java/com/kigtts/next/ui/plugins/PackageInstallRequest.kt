package com.kigtts.next.ui.plugins

import androidx.compose.runtime.*
import com.kigtts.next.i18n.rememberUiCopy
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.state.Destination
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

/** A store can request installation, but only native host review grants publisher trust/version replacement. */
@Composable
internal fun PackageInstallRequest(model: AppViewModel, request: Pair<String, PluginOutcome.InstallPackage>?, dismiss: () -> Unit) {
    if (request == null) return
    val (owner, outcome) = request
    var candidates by remember(request) { mutableStateOf<List<PackageInstallerInfo>?>(null) }
    var error by remember(request) { mutableStateOf<String?>(null) }
    var busy by remember(request) { mutableStateOf(false) }
    var completed by remember(request) { mutableStateOf(false) }
    val installed = remember(request) { mutableStateListOf<PackageInstallerInfo>() }
    val scope = rememberCoroutineScope()
    val copy = rememberUiCopy()
    LaunchedEffect(request) {
        val copied = mutableListOf<PendingPackage>()
        try {
            withContext(Dispatchers.IO) {
                require(outcome.dependencies.size <= 32) { copy("plugin.too_many_dependencies") }
                (outcome.dependencies + outcome.resource).forEach { resource ->
                    require(resource.pluginId == null || resource.pluginId == owner) { copy("plugin.invalid_install_owner") }
                    val source = model.pluginManager.paths.storage(owner).file(resource.path)
                    require(source.isFile && source.length() in 1..768L * 1024 * 1024) { copy("plugin.invalid_package_size") }
                    val frozen = File(model.pluginManager.paths.packages, ".incoming-${UUID.randomUUID()}.kigplugin")
                    try { source.copyTo(frozen); copied += PendingPackage(frozen, model.pluginManager.packages.inspect(frozen)) }
                    catch (failure: Exception) { frozen.delete(); throw failure }
                }
                require(copied.map { it.preview.manifest.id }.distinct().size == copied.size) { copy("plugin.install_duplicates") }
                val installed = model.pluginManager.packages.installed().associateBy { it.manifest.id }
                copied.forEach { candidate -> installed[candidate.preview.manifest.id]?.let { old ->
                    require(old.publisherSha256 == candidate.preview.publisherSha256) { copy("plugin.update_publisher_mismatch") }
                    require(candidate.preview.manifest.versionCode > old.manifest.versionCode || outcome.allowDowngrade && candidate.preview.manifest.versionCode != old.manifest.versionCode) { copy("plugin.installed_or_downgrade") }
                } }
            }
            candidates = withContext(Dispatchers.IO) {
                val previous = model.pluginManager.packages.installed().associateBy { it.manifest.id }
                copied.map { installerInfo(it, previous[it.preview.manifest.id]?.manifest) }
            }
        } catch (cancel: CancellationException) { copied.forEach { it.file.delete() }; throw cancel }
        catch (failure: Exception) { copied.forEach { it.file.delete() }; error = failure.message ?: copy("plugin.install_plan_failed") }
    }
    DisposableEffect(candidates) { val files = candidates.orEmpty().map { it.candidate.file }; onDispose { files.forEach(File::delete) } }
    PackageInstallerDialog(candidates, busy, completed, error, outcome.allowDowngrade, installed.map { it.manifest.name }, install = {
        scope.launch {
            busy = true; error = null
            try {
                model.stopSpeech()
                candidates.orEmpty().filterNot { candidate -> installed.any { it.manifest.id == candidate.manifest.id } }.forEach { info ->
                    val candidate = info.candidate
                    model.pluginManager.install(candidate.file, candidate.preview.publisherSha256, outcome.allowDowngrade)
                    installed += info
                }
                model.refreshPlugins(); completed = true
            } catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) {
                model.refreshPlugins()
                error = failure.message ?: copy("plugin.install_incomplete")
            } finally { busy = false }
        }
    }, dismiss = {
        dismiss()
        // Installation is a modal overlay, so the underlying page does not receive a new visit.
        // Replay only its declared entry action to reconcile the install/cancel result immediately.
        val page = model.state.value.destination as? Destination.PluginPage
        if (page?.pluginId == owner) model.registry.plugin(owner)?.page(page.pageId, model.context())
            ?.onEnterAction?.let { model.invoke(owner, it) }
    })
}
