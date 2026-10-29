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
    val identities by model.identities.items.collectAsStateWithLifecycle()
    var detailPeer by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(panel) { if (panel == null) detailPeer = null }
    val permission = rememberFacePermissionRequest(model::message)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, panel?.mode) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) controller.stopDiscovery()
            if (event == Lifecycle.Event.ON_START && panel?.mode in setOf(FaceMode.RECEIVE, FaceMode.SEND)) controller.discover()
        }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
    panel?.let { shown ->
        var original by remember(shown.ids) { mutableStateOf(false) }
        var composed by remember(shown.ids) { mutableStateOf(true) }
        LaunchedEffect(shown.mode) { if (shown.mode == FaceMode.RECEIVE) permission(true) { controller.receive() } }
        FaceShareSheet(if (shown.mode == FaceMode.RECEIVE) "面对面接收" else if (shown.personal) "面对面分享名片" else "面对面返图", controller::hide) {
                when (shown.mode) {
                    FaceMode.CHOOSE -> {
                        if (shown.personal) identities.firstOrNull()?.let {
                            com.kigtts.next.ui.identity.PersonalCardView(it, model.stickers, Modifier.fillMaxWidth(), interactive = false)
                        } else {
                            Text("已选 ${shown.ids.size} 张照片", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FaceFormatSwitch("原图", "原始清晰度", Icons.Default.Image, original, !busy) { original = it }
                            FaceFormatSwitch("拍立得成片", "保留相框与贴纸", Icons.Default.Photo, composed, !busy) { composed = it }
                        }
                        Button(feedbackClick { controller.prepare(FaceMode.BROWSER, original, composed) }, Modifier.fillMaxWidth(), enabled = !busy && (original || composed)) {
                            Icon(Icons.Default.QrCode, null); Spacer(Modifier.width(8.dp)); Text(if (shown.personal) "扫码查看名片" else "扫码领取")
                        }
                        FilledTonalButton(feedbackClick { permission(true) { controller.prepare(FaceMode.SEND, original, composed) } }, Modifier.fillMaxWidth(), enabled = !busy && (original || composed)) {
                            Icon(Icons.Default.Devices, null); Spacer(Modifier.width(8.dp)); Text("附近互传")
                        }
                    }
                    FaceMode.BROWSER -> {
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
                    FaceMode.SEND -> {
                        Text(if (shown.personal) "点选对方，分享名片" else "点选对方，发送照片", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (peers.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.size(24.dp)); Text("正在查找附近的 KIGTTS")
                        }
                        peers.forEach { peer -> OutlinedCard(feedbackClick { controller.send(peer) }, Modifier.fillMaxWidth(), enabled = !busy) {
                            ListItem(headlineContent = { Text(peer.name.ifBlank { "KIGTTS 用户" }) }, leadingContent = { FacePeerAvatar(peer.avatar) },
                                supportingContent = { Text(if (peer.host != null) "同一 Wi-Fi" else "附近设备") }, trailingContent = {
                                    ToolButton("查看个人主页", Icons.Default.ContactPage, { detailPeer = peer.id }, colors = compactPlainIconColors())
                                })
                        } }
                    }
                    FaceMode.RECEIVE -> {
                        Icon(Icons.Default.Radar, null, Modifier.align(Alignment.CenterHorizontally).size(64.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("请让对方打开“面对面返图”，点选你的头像。")
                        identities.firstOrNull()?.let {
                            com.kigtts.next.ui.identity.PersonalCardView(it, model.stickers, Modifier.fillMaxWidth(), interactive = false)
                        }
                    }
                }
                if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodyMedium)
                if (busy) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                if (shown.mode != FaceMode.CHOOSE) TextButton(feedbackClick(controller::stop), Modifier.fillMaxWidth()) { Text("结束分享") }
        }
    }
    if (panel != null) peers.firstOrNull { it.id == detailPeer }?.let { peer ->
        val profile = remember(peer.profile, peer.name, peer.avatar) {
            runCatching { peer.profile?.let { org.json.JSONObject(it) } }.getOrNull() ?: org.json.JSONObject().put("nickname", peer.name).put("avatar", peer.avatar)
        }
        FaceReceiptDialog(profile) { detailPeer = null }
    }
    requests.firstOrNull()?.let { offer -> FaceReceivePrompt(offer, controller::approve, controller::reject) }
}

@Composable
private fun FaceFormatSwitch(title: String, summary: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(summary) }, leadingContent = { Icon(icon, null) },
        trailingContent = { Switch(checked, feedbackChange(change), enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow))
}
