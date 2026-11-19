package com.kigtts.next.ui.home

import com.kigtts.next.i18n.uiText

import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.kigtts.next.media.WebNetworkGate
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.ChoiceButtonGroup
import com.kigtts.plugin.runtime.PermissionAccess

@Composable internal fun PhraseNetworkSettings(draft: PhraseDraft, model: AppViewModel, enabled: Boolean) {
    val ledger = model.pluginManager.permissions
    val owner = WebNetworkGate.owner(draft.phraseId ?: draft.key)
    var access by remember(owner) { mutableStateOf(ledger.access(owner, "network")) }
    Text(uiText("quick.external_network"), style = MaterialTheme.typography.titleSmall)
    ChoiceButtonGroup("外部网络", PermissionAccess.entries, access,
        { when (it) { PermissionAccess.ASK -> "首次询问"; PermissionAccess.ALLOW -> "允许"; PermissionAccess.DENY -> "禁用" } },
        { access = it; ledger.set(owner, "network", it) }, enabled = enabled, compactLabels = true)
    Text(uiText("quick.web_scope"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TextButton(onClick = { ledger.clearPrefix("web-device:${draft.phraseId ?: draft.key}:"); model.message(model.hostCopy("quick.device_grants_removed")) }, enabled = enabled) { Text(uiText("quick.device_grants_remove")) }
}
