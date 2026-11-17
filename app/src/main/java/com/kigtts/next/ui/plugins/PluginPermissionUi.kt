package com.kigtts.next.ui.plugins

import com.kigtts.next.i18n.uiText
import com.kigtts.next.i18n.rememberUiCopy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.runtime.*

@Composable internal fun PluginPermissionPrompts(model: AppViewModel) {
    val ledger = model.pluginManager.permissions
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(ledger, lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) ledger.foreground = true else ledger.background()
        }
        ledger.foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); ledger.background() }
    }
    val pending by ledger.pending.collectAsStateWithLifecycle()
    pending.firstOrNull()?.let { prompt -> key(prompt.token) {
        MotionAlertDialog(onDismissRequest = { ledger.decide(prompt.token, null) }, title = { PermissionDialogTitle(prompt) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(prompt.purpose); if (prompt.detail.isNotBlank()) Text(prompt.detail, style = MaterialTheme.typography.bodySmall) } },
            confirmButton = { MotionTextButton(onClick = dialogFeedbackClick { ledger.decide(prompt.token, PermissionAccess.ALLOW) }) { Text(uiText("permission.grant")) } },
            dismissButton = { Row {
                MotionTextButton(onClick = dialogFeedbackClick { ledger.decide(prompt.token, PermissionAccess.DENY) }) { Text(uiText("permission.deny")) }
                MotionTextButton(onClick = dialogFeedbackClick { ledger.decide(prompt.token, PermissionAccess.ASK) }) { Text(uiText("permission.once")) }
            } })
    } }
}

@Composable internal fun PluginPermissionSettings(model: AppViewModel, pluginId: String, projectId: String?, close: () -> Unit) {
    val copy = rememberUiCopy()
    val manager = model.pluginManager
    val native = manager.status.firstOrNull { it.installed.manifest.id == pluginId }?.installed
    val project = manager.registry.plugin(pluginId)?.contributions?.managedPackages?.firstOrNull { it.id == projectId }
    val name = project?.name ?: native?.manifest?.name ?: pluginId
    val permissions = (project?.permissions ?: native?.manifest?.capabilities.orEmpty().toSet()).intersect(PluginPermissions.sensitive).sorted()
    val purposes = project?.permissionPurposes ?: native?.manifest?.permissionPurposes.orEmpty()
    val owner = manager.permissionOwner(pluginId, projectId)
    var revision by remember { mutableIntStateOf(0) }
    MotionAlertDialog(onDismissRequest = close, title = { Text(uiText("permission.settings", "plugin" to name)) },
        text = { Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (permissions.isEmpty()) Text(uiText("permission.none"))
            permissions.forEach { permission -> key(permission, revision) {
                Text(pluginPermissionLabel(permission), style = MaterialTheme.typography.titleSmall)
                purposes[permission]?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                ChoiceButtonGroup(pluginPermissionLabel(permission), PermissionAccess.entries, manager.permissions.access(owner, permission),
                    { when (it) { PermissionAccess.ASK -> copy("permission.ask"); PermissionAccess.ALLOW -> copy("permission.allow"); PermissionAccess.DENY -> copy("permission.deny") } },
                    { manager.permissions.set(owner, permission, it); revision++ }, compactLabels = true)
            } }
        } }, confirmButton = { MotionTextButton(onClick = dialogFeedbackClick(close)) { Text(uiText("action.done")) } })
}
