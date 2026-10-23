package com.kigtts.next.ui.settings

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kigtts.next.overlay.*
import com.kigtts.next.ui.components.MaterialSymbols
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.feedbackChange
import com.kigtts.next.ui.components.MotionAlertDialog as AlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun OverlaySettingsScreen() {
    val context = LocalContext.current
    val store = remember { OverlayStore.get(context) }
    val state by store.settings.collectAsState()
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
    SettingsLayout {
        SettingsGroup("悬浮快捷入口") {
            ToggleSetting("开启悬浮窗", "在其它应用上方快速打开大字幕、相机和常用功能", Icons.Default.BubbleChart,
                state.enabled && Settings.canDrawOverlays(context), { enabled ->
                    if (enabled && !Settings.canDrawOverlays(context)) permissionExplanation = true else enable(enabled)
                }, position = SettingPosition.FIRST)
            ToggleSetting("自动贴边收起", "闲置时收起并淡化，点按展开，拖动调整位置", Icons.Default.Dock,
                state.autoHide, { enabled -> store.update { it.copy(autoHide = enabled) } }, position = SettingPosition.MIDDLE)
            ToggleSetting("显示入口文字", "关闭后可长按图标查看名称", Icons.Default.Label,
                state.showLabels, { enabled -> store.update { it.copy(showLabels = enabled) } }, position = SettingPosition.MIDDLE)
            ToggleSetting("优先小窗打开", "从悬浮入口打开应用和页面时优先使用小窗", Icons.Default.PictureInPictureAlt,
                state.preferSmallWindows, { enabled -> store.update { it.copy(preferSmallWindows = enabled) } }, position = SettingPosition.LAST)
        }
        SettingsGroup("快捷入口", "拖动手柄调整顺序，转动展开的入口列表可查看更多") {
            state.shortcuts.forEachIndexed { index, item ->
                val threshold = with(LocalDensity.current) { 64.dp.toPx() }
                SettingItem(item.title, "", MaterialSymbols.vector(item.symbol) ?: Icons.Default.Extension,
                    if (state.shortcuts.size == 1) SettingPosition.SINGLE else if (index == 0) SettingPosition.FIRST else if (index == state.shortcuts.lastIndex) SettingPosition.LAST else SettingPosition.MIDDLE,
                    onClick = { editing = item }, trailing = {
                        Icon(Icons.Default.DragIndicator, "拖动排序${item.title}", Modifier.size(40.dp)
                            .semantics { customActions = listOf(CustomAccessibilityAction("上移") { store.move(item.id, -1); true }, CustomAccessibilityAction("下移") { store.move(item.id, 1); true }) }
                            .pointerInput(item.id) {
                                var amount = 0f
                                detectDragGestures(onDragStart = { amount = 0f }, onDrag = { change, delta ->
                                    change.consume(); amount += delta.y
                                    if (kotlin.math.abs(amount) >= threshold) { store.move(item.id, if (amount > 0) 1 else -1); amount = 0f }
                                })
                            })
                    })
            }
            FilledTonalButton(onClick = feedbackClick { add = true }, Modifier.fillMaxWidth(), enabled = state.shortcuts.size < 24) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("添加入口")
            }
        }
    }
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
                Row { TextButton(onClick = feedbackClick { store.move(item.id, -1) }, enabled = state.shortcuts.firstOrNull()?.id != item.id) { Text("上移") }
                    TextButton(onClick = feedbackClick { store.move(item.id, 1) }, enabled = state.shortcuts.lastOrNull()?.id != item.id) { Text("下移") } }
            }
        }, confirmButton = { TextButton(onClick = feedbackClick { editing = null }) { Text("完成") } },
            dismissButton = { TextButton(onClick = feedbackClick { store.update { it.copy(shortcuts = it.shortcuts.filterNot { old -> old.id == item.id }) }; editing = null }) { Text("移除入口") } })
    }
}

@Composable private fun OverlayEntrancePicker(existing: Set<String>, onDismiss: () -> Unit, add: (OverlayShortcut) -> Unit) {
    val context = LocalContext.current
    val pluginEntries by remember { OverlayHub.get(context).candidates }.collectAsState()
    var apps by remember { mutableStateOf<List<OverlayShortcut>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) {
        context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.exported && it.activityInfo.enabled && it.activityInfo.packageName != context.packageName }
            .map { result -> val component = ComponentName(result.activityInfo.packageName, result.activityInfo.name).flattenToString()
                OverlayShortcut("app:$component", result.loadLabel(context.packageManager).toString(), "apps", component = component)
            }.distinctBy { it.id }.sortedBy { it.title.lowercase() }
    } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("添加入口") }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            OutlinedTextField(query, { query = it }, label = { Text("搜索入口") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
            LazyColumn(Modifier.weight(1f, fill = false)) {
                val groups = listOf("KIGTTS" to (defaultOverlayShortcuts() + OverlayShortcut("overlay-settings", "悬浮窗设置", "tune", "overlay-settings")), "插件" to pluginEntries, "应用" to apps)
                groups.forEach { (title, entries) ->
                    val visible = entries.filter { it.id !in existing && it.title.contains(query, true) }
                    if (visible.isNotEmpty()) {
                        item { Text(title, Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                        items(visible, key = { it.id }) { entry -> TextButton(onClick = feedbackClick { add(entry) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(MaterialSymbols.vector(entry.symbol) ?: Icons.Default.Extension, null); Spacer(Modifier.width(12.dp)); Text(entry.title, Modifier.weight(1f))
                        } }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = feedbackClick(onDismiss)) { Text("取消") } })
}
