package com.kigtts.next.ui.settings

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
    val displayEntries = resolveOverlayShortcuts(state.shortcuts, candidates, availableApps)
    var permissionExplanation by remember { mutableStateOf(false) }
    var add by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<OverlayShortcut?>(null) }
    fun enable(value: Boolean) {
        store.update { it.copy(enabled = value) }
        if (!NativeOverlayService.sync(context) && value) android.widget.Toast.makeText(context, "请先允许悬浮窗权限", android.widget.Toast.LENGTH_SHORT).show()
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Settings.canDrawOverlays(context)) enable(true)
    }
    OverlaySettingsList(displayEntries, store::order, { editing = it }, { add = true }, remove = { entry ->
        store.update { it.copy(shortcuts = it.shortcuts.filterNot { old -> old.id == entry.id }) }
    }, controls = {
        SettingsGroup("悬浮快捷入口") {
            ToggleSetting("开启悬浮窗", "快速打开字幕板、相机和常用功能", Icons.Default.BubbleChart,
                state.enabled && Settings.canDrawOverlays(context), { enabled ->
                    if (enabled && !Settings.canDrawOverlays(context)) permissionExplanation = true else enable(enabled)
                }, position = SettingPosition.FIRST)
            ToggleSetting("自动贴边收起", "闲置时收起并淡化，点按展开，拖动调整位置", Icons.Default.Dock,
                state.autoHide, { enabled -> store.update { it.copy(autoHide = enabled) } }, position = SettingPosition.MIDDLE)
            ToggleSetting("显示入口文字", "关闭后可长按图标查看名称", Icons.Default.Label,
                state.showLabels, { enabled -> store.update { it.copy(showLabels = enabled) } }, position = SettingPosition.MIDDLE)
            ToggleSetting("优先小窗打开", "在其它应用中使用小窗，KIGTTS 前台保持当前窗口", Icons.Default.PictureInPictureAlt,
                state.preferSmallWindows, { enabled -> store.update { it.copy(preferSmallWindows = enabled) } }, position = SettingPosition.LAST)
        }
    })
    if (permissionExplanation) AlertDialog(onDismissRequest = { permissionExplanation = false },
        icon = { Icon(Icons.Default.BubbleChart, null) }, title = { Text("允许显示悬浮窗") },
        text = { Text("开启后，快捷入口会显示在其它应用上方。请在接下来的系统页面允许 KIGTTS 显示悬浮窗。") },
        confirmButton = { TextButton(onClick = feedbackClick { permissionExplanation = false
            runCatching { permission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) }
                .onFailure { android.widget.Toast.makeText(context, "请在系统设置中开启悬浮窗权限", android.widget.Toast.LENGTH_SHORT).show() }
        }) { Text("前往设置") } }, dismissButton = { TextButton(onClick = feedbackClick { permissionExplanation = false }) { Text("稍后再说") } })
    if (add) OverlayEntrancePicker(state.shortcuts.map { it.id }.toSet(), onDismiss = { add = false }) { item ->
        store.update { it.copy(shortcuts = it.shortcuts + item) }; add = false
    }
    editing?.let { item ->
        val current = state.shortcuts.firstOrNull { it.id == item.id }
        if (current != null) AlertDialog(onDismissRequest = { editing = null }, title = { Text(current.title) }, text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("显示文字", Modifier.weight(1f)); Switch(current.showLabel, feedbackChange { enabled -> store.update { it.copy(shortcuts = it.shortcuts.map { old -> if (old.id == item.id) old.copy(showLabel = enabled) else old }) } }) }
            }
        }, confirmButton = { TextButton(onClick = feedbackClick { editing = null }) { Text("完成") } },
            dismissButton = {})
    }
}
