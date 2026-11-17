package com.kigtts.next.ui.settings

import com.kigtts.next.i18n.uiText

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kigtts.next.overlay.*
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.feedbackChange
import com.kigtts.next.ui.components.MotionAlertDialog as AlertDialog

@Composable
fun OverlaySettingsScreen() {
    val context = LocalContext.current
    val copy = com.kigtts.next.i18n.rememberUiCopy()
    val store = remember { OverlayStore.get(context) }
    val state by store.settings.collectAsState()
    val candidates by remember { OverlayHub.get(context).candidates }.collectAsState()
    var availableApps by remember { mutableStateOf(state.shortcuts.filter { it.component != null }.map { it.id }.toSet()) }
    LaunchedEffect(state.shortcuts) {
        val runtime = com.kigtts.next.manager.HostPluginRuntime.get(context.applicationContext as android.app.Application)
        while (true) {
            refreshOverlayPlugins(context, runtime.manager)
            availableApps = availableOverlayApps(context, state.shortcuts)
            kotlinx.coroutines.delay(1500)
        }
    }
    val displayEntries = resolveOverlayShortcuts(state.shortcuts, candidates, availableApps).map { item ->
        item.copy(title = item.localizedTitle { copy(it) })
    }
    var permissionExplanation by remember { mutableStateOf(false) }
    var add by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<OverlayShortcut?>(null) }
    fun enable(value: Boolean) {
        store.update { it.copy(enabled = value) }
        if (!NativeOverlayService.sync(context) && value) android.widget.Toast.makeText(context, copy("overlay.allow.toast"), android.widget.Toast.LENGTH_SHORT).show()
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Settings.canDrawOverlays(context)) enable(true)
    }
    OverlaySettingsList(displayEntries, store::order, { editing = it }, { add = true }, remove = { entry ->
        store.update { it.copy(shortcuts = it.shortcuts.filterNot { old -> old.id == entry.id }) }
    }, controls = {
        SettingsGroup(uiText("overlay.entry.title")) {
            ToggleSetting(uiText("overlay.on"), uiText("overlay.on.body"), Icons.Default.BubbleChart,
                state.enabled && Settings.canDrawOverlays(context), { enabled ->
                    if (enabled && !Settings.canDrawOverlays(context)) permissionExplanation = true else enable(enabled)
                }, position = SettingPosition.FIRST)
            ToggleSetting(uiText("overlay.fold"), uiText("overlay.fold.body"), Icons.Default.Dock,
                state.autoHide, { enabled -> store.update { it.copy(autoHide = enabled) } }, position = SettingPosition.MIDDLE)
            ToggleSetting(uiText("overlay.labels"), uiText("overlay.labels.hint"), Icons.Default.Label,
                state.showLabels, { enabled -> store.update { it.copy(showLabels = enabled) } }, position = SettingPosition.MIDDLE)
            ToggleSetting(uiText("overlay.window"), uiText("overlay.window.body"), Icons.Default.PictureInPictureAlt,
                state.preferSmallWindows, { enabled -> store.update { it.copy(preferSmallWindows = enabled) } }, position = SettingPosition.LAST)
        }
    })
    if (permissionExplanation) AlertDialog(onDismissRequest = { permissionExplanation = false },
        icon = { Icon(Icons.Default.BubbleChart, null) }, title = { Text(uiText("overlay.allow")) },
        text = { Text(uiText("overlay.permission")) },
        confirmButton = { TextButton(onClick = feedbackClick { permissionExplanation = false
            runCatching { permission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) }
                .onFailure { android.widget.Toast.makeText(context, copy("overlay.allow.toast"), android.widget.Toast.LENGTH_SHORT).show() }
        }) { Text(uiText("overlay.settings")) } }, dismissButton = { TextButton(onClick = feedbackClick { permissionExplanation = false }) { Text(uiText("overlay.later")) } })
    if (add) OverlayEntrancePicker(state.shortcuts.map { it.id }.toSet(), onDismiss = { add = false }) { item ->
        store.update { it.copy(shortcuts = it.shortcuts + item) }; add = false
    }
    editing?.let { item ->
        val current = state.shortcuts.firstOrNull { it.id == item.id }
        if (current != null) AlertDialog(onDismissRequest = { editing = null }, title = { Text(current.localizedTitle { copy(it) }) }, text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(uiText("overlay.labels.short"), Modifier.weight(1f)); Switch(current.showLabel, feedbackChange { enabled -> store.update { it.copy(shortcuts = it.shortcuts.map { old -> if (old.id == item.id) old.copy(showLabel = enabled) else old }) } }) }
            }
        }, confirmButton = { TextButton(onClick = feedbackClick { editing = null }) { Text(uiText("action.done")) } },
            dismissButton = {})
    }
}
