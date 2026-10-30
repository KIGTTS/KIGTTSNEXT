@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.sharing

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.components.*

@Composable
internal fun FaceShareHost(model: AppViewModel) {
    val controller = model.faceShare
    val panel by controller.panel.collectAsStateWithLifecycle()
    val requests by controller.requests.collectAsStateWithLifecycle()
    val busy by controller.busy.collectAsStateWithLifecycle()
    val status by controller.status.collectAsStateWithLifecycle()
    val progress by controller.progress.collectAsStateWithLifecycle()
    val url by controller.url.collectAsStateWithLifecycle()
    val hotspot by controller.hotspot.details.collectAsStateWithLifecycle()
    val peers by controller.peers.collectAsStateWithLifecycle()
    val receiving by controller.receiving.collectAsStateWithLifecycle()
    val identities by model.identities.items.collectAsStateWithLifecycle()
    val catalog by model.stickers.snapshot.collectAsStateWithLifecycle()
    val permission = rememberFacePermissionRequest(model::message)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, panel?.mode, receiving) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) controller.foreground(false)
            if (event == Lifecycle.Event.ON_START) controller.foreground(true)
        }
        controller.foreground(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(receiving) { if (receiving && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) controller.startDiscovery() }
    panel?.let { shown ->
        var format by remember(shown.ids) { mutableStateOf(FacePhotoFormat.COMPOSED) }
        LaunchedEffect(shown.mode, shown.ids) {
            if (shown.mode in setOf(FaceMode.SEND, FaceMode.RECEIVE)) controller.startDiscovery()
        }
        FaceShareSheet(if (shown.mode == FaceMode.RECEIVE) "面对面接收" else if (shown.personal) "分享名片" else "分享照片", controller::hide,
            maximumHeight = if (shown.mode == FaceMode.SEND) { if (shown.personal) 420.dp else 520.dp } else 720.dp) {
                when (shown.mode) {
                    FaceMode.SEND -> {
                        FaceSharePreview(if (shown.personal) identities.firstOrNull() else null,
                            shown.ids.mapNotNull { id -> catalog.cards.firstOrNull { it.id == id } }, model.stickers)
                        if (!shown.personal) FacePhotoFormatChoice(format) { if (!busy) format = it }
                        Spacer(Modifier.height(4.dp))
                        FaceDiscoveryBar(peers, busy,
                            share = { controller.shareSystem(format.original, format.composed) },
                            wifi = { controller.prepareBrowser(format.original, format.composed) },
                            send = { controller.send(it, format.original, format.composed) })
                        if (peers.isEmpty() && !busy) FaceDiscoveryHelp { permission(true, controller::refreshDiscovery) }
                    }
                    FaceMode.BROWSER -> {
                        TextButton(feedbackClick(controller::returnToShare)) { Icon(Icons.Default.ArrowBack, null); Spacer(Modifier.width(6.dp)); Text("返回分享") }
                        hotspot?.let { connection ->
                            Text("1 · 连接 Wi-Fi", style = MaterialTheme.typography.titleMedium)
                            FaceQrCode(connection.qr(), "连接热点二维码")
                            Text(connection.ssid); Text("密码：${connection.password}", style = MaterialTheme.typography.bodyMedium)
                            Text("2 · 扫码领取照片", style = MaterialTheme.typography.titleMedium)
                        } ?: run {
                            Text("在同一 Wi-Fi 下，直接扫描领取码。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(feedbackClick { permission(false, controller::startHotspot) }, Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.WifiTethering, null); Spacer(Modifier.width(8.dp)); Text("创建临时热点")
                            }
                        }
                        if (url != null) FaceQrCode(requireNotNull(url), "领取照片二维码")
                        else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.size(24.dp)); Text("等待 Wi-Fi 连接")
                        }
                        Text(if (shown.personal) "对方用浏览器查看你的名片和联系方式。" else "对方用浏览器预览、选择并保存照片。", style = MaterialTheme.typography.bodyMedium)
                    }
                    FaceMode.RECEIVE -> {
                        Icon(Icons.Default.Radar, null, Modifier.align(Alignment.CenterHorizontally).size(64.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("请让对方打开分享，点选你的头像。")
                        identities.firstOrNull()?.let {
                            com.kigtts.next.ui.identity.PersonalCardView(it, model.stickers, Modifier.fillMaxWidth(), interactive = false)
                        }
                        FaceDiscoveryHelp { permission(true, controller::refreshDiscovery) }
                    }
                }
                if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodyMedium)
                if (busy) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                if (shown.mode in setOf(FaceMode.RECEIVE, FaceMode.BROWSER)) TextButton(feedbackClick(controller::stop), Modifier.fillMaxWidth()) { Text("结束分享") }
        }
    }
    FaceReceiveBanner(requests.firstOrNull(), controller::approve, controller::reject)
}
