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
            StoreBootstrapPhase.OFFER -> "先安装插件商店"
            StoreBootstrapPhase.RESTORE -> "启用插件商店"
            StoreBootstrapPhase.DOWNLOADING -> "正在下载插件商店"
            StoreBootstrapPhase.REVIEW -> "安装插件商店？"
            StoreBootstrapPhase.INSTALLING -> "正在准备插件商店"
            StoreBootstrapPhase.COMPLETED -> "插件已安装"
            StoreBootstrapPhase.ERROR -> "插件商店暂不可用"
        }) },
        text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(when (state.phase) {
                StoreBootstrapPhase.OFFER -> if (synthesis) uiText("store.reading") else "添加插件源需要插件商店。是否从 KIGTTS 官方源下载并安装？"
                StoreBootstrapPhase.RESTORE -> if (synthesis) "插件商店已安装但当前不可用。启用后前往安装语音合成服务。" else "插件商店已安装但当前不可用。启用后继续添加源。"
                StoreBootstrapPhase.DOWNLOADING -> uiText("store.fetch")
                StoreBootstrapPhase.REVIEW -> "下载已完成，请确认插件信息。"
                StoreBootstrapPhase.INSTALLING -> if (synthesis) "完成后前往语音合成服务的安装页面。" else "完成后继续添加刚才的源。"
                StoreBootstrapPhase.COMPLETED -> "商店已准备好。"
                StoreBootstrapPhase.ERROR -> state.error.orEmpty()
            })
            state.candidate?.preview?.let { candidate ->
                Text("${candidate.manifest.name} · ${candidate.manifest.version} · ${candidate.manifest.license}")
                Text(uiText("plugin.capabilities_display", "capabilities" to candidate.manifest.capabilities.joinToString(", ")), style = MaterialTheme.typography.bodySmall)
                Text(uiText("plugin.publisher_digest", "digest" to candidate.publisherSha256), style = MaterialTheme.typography.bodySmall)
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(if (synthesis) "商店准备好后，请选择语音合成服务并确认安装。" else uiText("store.source"), style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { MotionTextButton(onClick = feedbackClick(confirm), enabled = !busy) { Text(when (state.phase) {
            StoreBootstrapPhase.OFFER -> "下载插件商店"
            StoreBootstrapPhase.RESTORE -> "启用并继续"
            StoreBootstrapPhase.REVIEW -> "安装并继续"
            StoreBootstrapPhase.COMPLETED -> "安装完成"
            else -> "重试"
        }) } },
        dismissButton = { MotionTextButton(onClick = dialogFeedbackClick(dismiss), enabled = state.phase != StoreBootstrapPhase.INSTALLING) {
            Text(if (state.phase == StoreBootstrapPhase.DOWNLOADING) "取消下载" else "取消")
        } })
}
