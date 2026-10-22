package com.kigtts.next.ui.plugins

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.plugins.control.*
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.components.*

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
    MotionAlertDialog(onDismissRequest = dismiss, dismissEnabled = state.phase != StoreBootstrapPhase.INSTALLING,
        icon = { Icon(Icons.Default.Storefront, contentDescription = null) },
        title = { Text(when (state.phase) {
            StoreBootstrapPhase.OFFER -> "先安装插件商店"
            StoreBootstrapPhase.RESTORE -> "启用插件商店"
            StoreBootstrapPhase.DOWNLOADING -> "正在下载插件商店"
            StoreBootstrapPhase.REVIEW -> "安装插件商店？"
            StoreBootstrapPhase.INSTALLING -> "正在准备插件商店"
            StoreBootstrapPhase.ERROR -> "插件商店暂不可用"
        }) },
        text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(when (state.phase) {
                StoreBootstrapPhase.OFFER -> "添加插件源需要插件商店。是否从 KIGTTS 官方源下载并安装？"
                StoreBootstrapPhase.RESTORE -> "插件商店已安装但当前不可用。启用后继续添加源。"
                StoreBootstrapPhase.DOWNLOADING -> "正在读取官方目录并检查下载文件…"
                StoreBootstrapPhase.REVIEW -> "下载已完成，请确认插件信息。"
                StoreBootstrapPhase.INSTALLING -> "完成后继续添加刚才的源。"
                StoreBootstrapPhase.ERROR -> state.error.orEmpty()
            })
            state.candidate?.preview?.let { candidate ->
                Text("${candidate.manifest.name} · ${candidate.manifest.version} · ${candidate.manifest.license}")
                Text("能力：${candidate.manifest.capabilities.joinToString("、")}", style = MaterialTheme.typography.bodySmall)
                Text("发布者 SHA-256：${candidate.publisherSha256}", style = MaterialTheme.typography.bodySmall)
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("源地址已保留；商店准备好后仍会请你确认添加。", style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { MotionTextButton(onClick = feedbackClick(confirm), enabled = !busy) { Text(when (state.phase) {
            StoreBootstrapPhase.OFFER -> "下载插件商店"
            StoreBootstrapPhase.RESTORE -> "启用并继续"
            StoreBootstrapPhase.REVIEW -> "安装并继续"
            else -> "重试"
        }) } },
        dismissButton = { MotionTextButton(onClick = dialogFeedbackClick(dismiss), enabled = state.phase != StoreBootstrapPhase.INSTALLING) {
            Text(if (state.phase == StoreBootstrapPhase.DOWNLOADING) "取消下载" else "取消")
        } })
}
