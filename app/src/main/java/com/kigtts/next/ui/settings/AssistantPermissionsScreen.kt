package com.kigtts.next.ui.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.i18n.uiText
import com.kigtts.next.state.ASSISTANT_SETTINGS_ID
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.navigation.LocalPageActive
import com.kigtts.next.ui.plugins.PermissionAccessChoices
import com.kigtts.next.ui.plugins.PluginBlocks
import com.kigtts.next.ui.plugins.permissionIcon
import com.kigtts.next.ui.plugins.pluginPermissionLabel
import com.kigtts.next.ui.plugins.settingPosition
import com.kigtts.plugin.api.PluginPage
import com.kigtts.plugin.runtime.PluginPermissions

@Composable
internal fun AssistantPermissionsScreen(model: AppViewModel, page: PluginPage?, enabled: Boolean) {
    val manager = model.pluginManager
    val manifest = manager.status.firstOrNull { it.installed.manifest.id == ASSISTANT_SETTINGS_ID }?.installed?.manifest
    val permissions = manifest?.capabilities.orEmpty().filter { it in PluginPermissions.sensitive }.distinct().sorted()
    val owner = manager.permissionOwner(ASSISTANT_SETTINGS_ID)
    val active = LocalPageActive.current
    SettingsLayout {
        PluginBlocks(page?.blocks.orEmpty(), enabled && active, { action, inputs ->
            model.invoke(ASSISTANT_SETTINGS_ID, action, inputs)
        })
        SettingsGroup(uiText("assistant.settings.app_permissions")) {
            AssistantAppPermissionRows(manager.permissions, owner, permissions, manifest?.permissionPurposes.orEmpty(), active)
        }
    }
}

@Composable
internal fun AssistantAppPermissionRows(ledger: PluginPermissions, owner: String, permissions: List<String>,
    purposes: Map<String, String>, enabled: Boolean) {
    if (permissions.isEmpty()) Text(uiText("permission.none"))
    val pending by ledger.pending.collectAsStateWithLifecycle()
    val active = LocalPageActive.current
    var revision by remember(ledger, owner) { mutableIntStateOf(0) }
    val access = remember(ledger, owner, permissions, pending, active, revision) {
        permissions.associateWith { ledger.access(owner, it) }
    }
    permissions.forEachIndexed { index, permission -> key(permission) {
        val label = pluginPermissionLabel(permission)
        SettingItem(label, purposes[permission].orEmpty(), permissionIcon(permission), settingPosition(index, permissions.size)) {
            PermissionAccessChoices(label, access.getValue(permission), enabled) { decision ->
                ledger.set(owner, permission, decision)
                revision++
            }
        }
    } }
}
