package com.kigtts.next.sharing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.identity.rememberAvatarShape

@Composable
internal fun FaceDiscoveryBar(peers: List<FacePeer>, busy: Boolean, share: () -> Unit, wifi: () -> Unit, send: (FacePeer) -> Unit,
    connection: (() -> Unit)? = null) {
    LazyRow(Modifier.fillMaxWidth().testTag("face-discovery-bar"), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        item("share") { DiscoveryAction("分享", Icons.Default.Share, busy, share) }
        item("wifi") { DiscoveryAction("Wi-Fi 互传", Icons.Default.Wifi, busy, wifi) }
        if (connection != null) item("connection") { DiscoveryAction("连接设备", Icons.Default.WifiTethering, busy, connection) }
        item("divider") { VerticalDivider(Modifier.height(58.dp).padding(bottom = 20.dp)) }
        items(peers, key = { "peer-${it.id}" }) { peer ->
            Column(Modifier.width(76.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(feedbackClick { if (!busy) send(peer) }, Modifier.size(64.dp).testTag("face-peer-${peer.id}").semantics {
                    contentDescription = "发送给 ${peer.name.ifBlank { "KIGTTS 用户" }}"; if (busy) disabled()
                },
                    shape = rememberAvatarShape(peer.avatarFrame), color = MaterialTheme.colorScheme.secondaryContainer) {
                    FacePeerAvatar(peer.avatar, Modifier.fillMaxSize(), peer.avatarFrame)
                }
                Text(peer.name.ifBlank { "KIGTTS 用户" }, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (peers.isEmpty()) item("searching") {
            Column(Modifier.width(76.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp) }
                Text("查找中", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun DiscoveryAction(label: String, icon: ImageVector, busy: Boolean, action: () -> Unit) {
    Column(Modifier.width(76.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(feedbackClick { if (!busy) action() }, Modifier.size(64.dp).testTag(when (label) {
            "分享" -> "face-system-share"; "连接设备" -> "face-connection"; else -> "face-wifi-share" })
            .semantics { if (busy) disabled() },
            shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(28.dp)) }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}
