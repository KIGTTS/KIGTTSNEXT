package com.kigtts.next.ui.plugins

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.plugins.control.*
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun StoreBootstrapPrompt(model: AppViewModel) {
    val coordinator = model.storeBootstrap
    val state by coordinator.state.collectAsStateWithLifecycle()
    StoreBootstrapPrompt(state, coordinator::confirm, coordinator::dismiss)
}

@Composable
internal fun StoreBootstrapPrompt(state: StoreBootstrapState?, confirm: () -> Unit, dismiss: () -> Unit) {
    if (state == null) return
    val busy = state.phase in setOf(StoreBootstrapPhase.DOWNLOADING, StoreBootstrapPhase.INSTALLING)
    val candidate = state.candidate
    if (candidate != null && state.phase in setOf(StoreBootstrapPhase.REVIEW, StoreBootstrapPhase.INSTALLING, StoreBootstrapPhase.COMPLETED)) {
        var info by remember(candidate) { mutableStateOf<PackageInstallerInfo?>(null) }
        LaunchedEffect(candidate) {
            val pending = PendingPackage(candidate.file, candidate.preview)
            info = withContext(Dispatchers.IO) { runCatching { installerInfo(pending) }.getOrElse { PackageInstallerInfo(pending) } }
        }
        PackageInstallerDialog(info?.let(::listOf), busy, state.phase == StoreBootstrapPhase.COMPLETED, null,
            install = confirm, dismiss = if (state.phase == StoreBootstrapPhase.COMPLETED) confirm else dismiss)
        return
    }
    val synthesis = state.purpose == StoreBootstrapPurpose.SYNTHESIS
    MotionAlertDialog(onDismissRequest = dismiss, dismissEnabled = state.phase != StoreBootstrapPhase.INSTALLING,
        icon = { Icon(Icons.Default.Storefront, contentDescription = null) },
        title = { Text(when (state.phase) {
            StoreBootstrapPhase.OFFER -> uiText("store.install_first")
            StoreBootstrapPhase.RESTORE -> uiText("store.enable")
            StoreBootstrapPhase.DOWNLOADING -> uiText("store.downloading")
            StoreBootstrapPhase.REVIEW -> uiText("store.install_prompt")
            StoreBootstrapPhase.INSTALLING -> uiText("store.preparing")
            StoreBootstrapPhase.COMPLETED -> uiText("store.installed")
            StoreBootstrapPhase.ERROR -> uiText("store.unavailable")
        }) },
        text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(when (state.phase) {
                StoreBootstrapPhase.OFFER -> if (synthesis) uiText("store.reading") else uiText("store.offer_source")
                StoreBootstrapPhase.RESTORE -> if (synthesis) uiText("store.restore_reading") else uiText("store.restore_source")
                StoreBootstrapPhase.DOWNLOADING -> uiText("store.fetch")
                StoreBootstrapPhase.REVIEW -> uiText("store.review_ready")
                StoreBootstrapPhase.INSTALLING -> if (synthesis) uiText("store.after_install_reading") else uiText("store.after_install_source")
                StoreBootstrapPhase.COMPLETED -> uiText("store.ready")
                StoreBootstrapPhase.ERROR -> state.error.orEmpty()
            })
            state.candidate?.preview?.let { candidate ->
                Text("${candidate.manifest.name} · ${candidate.manifest.version} · ${candidate.manifest.license}")
                Text(uiText("plugin.capabilities_display", "capabilities" to candidate.manifest.capabilities.joinToString(", ")), style = MaterialTheme.typography.bodySmall)
                Text(uiText("plugin.publisher_digest", "digest" to candidate.publisherSha256), style = MaterialTheme.typography.bodySmall)
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(if (synthesis) uiText("store.choose_voice_engine") else uiText("store.source"), style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { MotionTextButton(onClick = feedbackClick(confirm), enabled = !busy) { Text(when (state.phase) {
            StoreBootstrapPhase.OFFER -> uiText("store.download")
            StoreBootstrapPhase.RESTORE -> uiText("store.enable_continue")
            StoreBootstrapPhase.REVIEW -> uiText("store.install_continue")
            StoreBootstrapPhase.COMPLETED -> uiText("store.install_done")
            else -> uiText("store.retry")
        }) } },
        dismissButton = { MotionTextButton(onClick = dialogFeedbackClick(dismiss), enabled = state.phase != StoreBootstrapPhase.INSTALLING) {
            Text(if (state.phase == StoreBootstrapPhase.DOWNLOADING) uiText("store.cancel_download") else uiText("action.cancel"))
        } })
}
