@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.sharing

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.components.*
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable internal fun CloudNativeHost(model: AppViewModel) {
    val shown by model.sharing.nativePanel.collectAsState()
    val outgoing by model.sharing.outgoing.collectAsState()
    val receiving by model.sharing.receiving.collectAsStateWithLifecycle()
    val app by model.state.collectAsStateWithLifecycle()
    val idle = remember(app.pluginRevision, receiving) {
        if (receiving && model.sharing.setup.enabled) model.sharing.nativePage(CLOUD_SHARING_ID, "receive") else null
    }
    // Receiving remains independent while an outgoing share is shown; both phones may send.
    idle?.let { key("cloud-receiver", it) { CloudNativePanelHost(model, it, true, shown == it) } }
    outgoing?.let { share ->
        key("cloud-sender", share.key) {
            CloudNativePanelHost(model, share.panel, false, shown == share.panel, share.expiresAt)
        }
        LaunchedEffect(share, app.pluginRevision) {
            if (!model.sharing.isProvider(share.panel.owner)) model.sharing.endNativeShare(share.panel)
        }
    }
    shown?.takeIf { it != idle && !it.pageId.startsWith("outgoing/") }?.let {
        key("cloud-shown", it) { CloudNativePanelHost(model, it, false, true) }
    }
}

@Composable private fun CloudNativePanelHost(model: AppViewModel, panel: CloudNativePanel, receiving: Boolean, shown: Boolean,
    expiresAt: Long? = null) {
    val scope = rememberCoroutineScope()
    val session = remember(panel) { CloudNativeSession(model, panel, scope) }
    // Opening the share sheet also makes this device available for the current session.
    SideEffect { session.acceptsInvitations = receiving || shown }
    CloudNativeTransport(model, session, receiving || shown)
    var receivingDetails by remember(session) { mutableStateOf(false) }
    if (shown || receivingDetails) {
        FaceShareSheet(if (session.sending && !session.incomingContent) "分享" else "面对面接收", {
            receivingDetails = false; if (shown) model.sharing.dismissNative(panel)
        }, maximumHeight = if (session.sending) 560.dp else 680.dp,
            finish = expiresAt?.let { { model.sharing.endNativeShare(panel) } }) {
            CloudNativeContent(model, session)
            if (expiresAt != null && !session.incomingContent) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val time = remember(context, expiresAt) { android.text.format.DateFormat.getTimeFormat(context).format(expiresAt) }
                Text("链接有效至 $time，可重复领取。关闭面板后继续分享。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    session.invitation?.let { invite ->
        MotionAlertDialog(session::reject, title = { Text("${invite.profile.optString("name", "KIGTTS 用户")} 想与你分享") },
            text = { Text(invite.title.ifBlank { "收到一份分享" }) },
            confirmButton = { TextButton(dialogFeedbackClick { receivingDetails = true; session.accept() }) { Text("接收") } },
            dismissButton = { TextButton(dialogFeedbackClick(session::reject)) { Text("暂不") } })
    }
    session.request?.let {
        MotionAlertDialog({ session.decide(false) }, title = { Text("有人想查看这次分享") }, text = { Text("请确认正在与你连接的人。") },
            confirmButton = { TextButton(dialogFeedbackClick { session.decide(true) }) { Text("允许连接") } },
            dismissButton = { TextButton(dialogFeedbackClick { session.decide(false) }) { Text("暂不") } })
    }
}

@Composable private fun ColumnScope.CloudNativeContent(model: AppViewModel, session: CloudNativeSession) {
    var code by remember(session) { mutableStateOf(false) }
    if (session.sending && !session.incomingContent) {
        CloudNativePreview(model, session)
        FaceDiscoveryBar(session.peers, false,
            share = { session.url?.let { model.sharing.handle(session.panel.owner, "sharing/system-link-v1", mapOf("url" to it)) } },
            wifi = { code = !code }, send = session::send, connection = {
                session.url?.let { model.sharing.handle(session.panel.owner, "sharing/nfc-v1", mapOf("url" to it)) }
            }, wifiLabel = "二维码", connectionLabel = "碰一碰")
        androidx.compose.animation.AnimatedVisibility(code) {
            session.url?.let { FaceQrCode(it, "分享二维码") }
        }
    } else {
        CloudNativeReceived(model, session)
    }
    Text(session.status, Modifier.testTag("cloud-native-status"), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (session.busy) LinearWavyProgressIndicator(progress = { session.progress }, modifier = Modifier.fillMaxWidth())
}
