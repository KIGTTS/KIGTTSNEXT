package com.kigtts.next.ui.home

import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.kigtts.next.media.WebNetworkGate
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.ConnectedChoiceGroup
import com.kigtts.plugin.runtime.PermissionAccess

@Composable internal fun PhraseNetworkSettings(draft: PhraseDraft, model: AppViewModel, enabled: Boolean) {
    val ledger = model.pluginManager.permissions
    val owner = WebNetworkGate.owner(draft.phraseId ?: draft.key)
    var access by remember(owner) { mutableStateOf(ledger.access(owner, "network")) }
    Text("外部网络", style = MaterialTheme.typography.titleSmall)
    ConnectedChoiceGroup("外部网络", PermissionAccess.entries, access,
        { when (it) { PermissionAccess.ASK -> "首次询问"; PermissionAccess.ALLOW -> "允许"; PermissionAccess.DENY -> "禁用" } },
        { access = it; ledger.set(owner, "network", it) }, enabled = enabled, compactLabels = true)
    Text("仅对此词条生效。本地页面文件始终可用。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TextButton(onClick = { ledger.clearPrefix("web-device:${draft.phraseId ?: draft.key}:"); model.message("已清除此词条的网页设备授权") }, enabled = enabled) { Text("清除摄像头、麦克风、传感器和下载授权") }
}
