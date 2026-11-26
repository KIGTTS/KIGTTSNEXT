package com.kigtts.next.ui.settings

import android.Manifest
import android.app.NotificationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.i18n.uiText
import com.kigtts.next.quickactions.LockScreenShortcut
import com.kigtts.next.quickactions.SubtitleGesture
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.components.feedbackChange
import com.kigtts.next.ui.home.QuickTextSelectorDialog
import com.kigtts.next.ui.navigation.LocalPageActive
import com.kigtts.next.ui.plugins.settingPosition
import com.kigtts.plugin.api.PluginOutcome

@Composable
internal fun QuickActionsSettings(model: AppViewModel) {
    val context = LocalContext.current
    val notifications = remember(context) { context.getSystemService(NotificationManager::class.java) }
    val prefs = model.quickActionPreferences
    val available = LocalPageActive.current
    var lock by remember { mutableStateOf(prefs.lockScreenShortcut) }
    var bluetooth by remember { mutableStateOf(prefs.bluetoothSubtitles) }
    var visible by remember { mutableStateOf(LockScreenShortcut.canPost(notifications)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        LockScreenShortcut.sync(context)
        visible = LockScreenShortcut.canPost(notifications)
    }
    LifecycleResumeEffect(context) {
        lock = prefs.lockScreenShortcut
        bluetooth = prefs.bluetoothSubtitles
        LockScreenShortcut.sync(context)
        visible = LockScreenShortcut.canPost(notifications)
        onPauseOrDispose { }
    }

    SettingsLayout {
        SettingsGroup(uiText("quick_actions.shortcuts")) {
            ToggleSetting(uiText("quick_actions.lock_screen"), uiText("quick_actions.lock_body"), Icons.Default.ScreenLockPortrait,
                lock, { enabled ->
                    lock = enabled
                    prefs.lockScreenShortcut = enabled
                    LockScreenShortcut.sync(context)
                    visible = LockScreenShortcut.canPost(notifications)
                    if (enabled && Build.VERSION.SDK_INT >= 33 && !notifications.areNotificationsEnabled())
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }, position = SettingPosition.FIRST, enabled = available)
            if (lock && !visible) SettingItem(uiText("quick_actions.lock_notification_settings"), uiText("quick_actions.lock_notification_blocked"),
                Icons.Default.Notifications, SettingPosition.MIDDLE, enabled = available,
                onClick = { context.startActivity(LockScreenShortcut.notificationSettings(context)) })
            ToggleSetting(uiText("quick_actions.bluetooth"), uiText("quick_actions.bluetooth_body"), Icons.Default.BluetoothAudio,
                bluetooth, { bluetooth = it; model.setBluetoothSubtitles(it) }, position = SettingPosition.LAST, enabled = available)
        }
        GestureShortcutSettings(model, available)
    }
}

@Composable
private fun GestureShortcutSettings(model: AppViewModel, available: Boolean) {
    val store = model.subtitleGestures
    val settings by store.state.collectAsStateWithLifecycle()
    val phrases by model.phrases.collectAsStateWithLifecycle()
    val categories by model.categories.collectAsStateWithLifecycle()
    val categoryIcons by model.quickTexts.categoryIcons.collectAsStateWithLifecycle()
    val byId = remember(phrases) { phrases.associateBy { it.id } }
    var choosing by remember { mutableStateOf<SubtitleGesture?>(null) }
    SettingsGroup(uiText("quick_actions.gestures")) {
        ToggleSetting(uiText("quick_actions.gesture_enable"), uiText("quick_actions.gesture_body"), Icons.Default.Gesture,
            settings.enabled, store::setEnabled, position = SettingPosition.FIRST, enabled = available)
        SubtitleGesture.entries.forEachIndexed { index, gesture ->
            val binding = settings.bindings[gesture]
            val title = uiText("quick_actions.gesture.${gesture.id}")
            val phrase = binding?.let { byId[it.phraseId] }
            SettingItem(title, phrase?.text?.trim()?.take(80)?.takeIf(String::isNotBlank) ?: uiText("quick_actions.gesture_choose"),
                null, settingPosition(index + 1, SubtitleGesture.entries.size + 1),
                enabled = available, onClick = { choosing = gesture }, leading = { GesturePreview(gesture) },
                trailing = {
                    if (binding != null && phrase != null) Switch(binding.enabled,
                        onCheckedChange = feedbackChange { store.enable(gesture, it) }, enabled = available)
                })
        }
    }
    if (available) choosing?.let { gesture ->
        QuickTextSelectorDialog(
            PluginOutcome.SelectQuickText(uiText("quick_actions.gesture_choose"), selectedIds = listOfNotNull(settings.bindings[gesture]?.phraseId)),
            phrases, categories, { choosing = null }, { chosen ->
                chosen.singleOrNull()?.let { store.bind(gesture, it) }
                choosing = null
            }, categoryIcons, model.mediaRepository)
    }
}

@Composable
private fun GesturePreview(gesture: SubtitleGesture) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(24.dp)) {
        val inset = 3.dp.toPx()
        fun Offset.onCanvas() = Offset(inset + x * (size.width - 2 * inset), inset + y * (size.height - 2 * inset))
        val points = gesture.vertices.map { it.onCanvas() }
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, color, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, 2.dp.toPx(), points.first())
    }
}
