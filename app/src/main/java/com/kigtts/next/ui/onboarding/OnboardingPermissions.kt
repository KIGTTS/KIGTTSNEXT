package com.kigtts.next.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.kigtts.next.i18n.uiText
import com.kigtts.next.ui.settings.SettingItem
import com.kigtts.next.ui.settings.SettingPosition
import com.kigtts.next.ui.settings.SettingsGroup

private data class PermissionOption(val permission: String, val title: String, val detail: String, val icon: ImageVector)

@Composable
internal fun OnboardingPermissions() {
    val context = LocalContext.current
    val options = remember {
        buildList {
            add(PermissionOption(Manifest.permission.RECORD_AUDIO, "permission.microphone", "onboarding.permission_microphone", Icons.Default.Mic))
            add(PermissionOption(Manifest.permission.CAMERA, "permission.camera", "onboarding.permission_camera", Icons.Default.PhotoCamera))
            if (Build.VERSION.SDK_INT >= 33) {
                add(PermissionOption(Manifest.permission.POST_NOTIFICATIONS, "permission.notifications",
                    "onboarding.permission_notifications", Icons.Default.Notifications))
            }
            add(PermissionOption(Manifest.permission.SYSTEM_ALERT_WINDOW, "onboarding.permission_overlay_name",
                "onboarding.permission_overlay", Icons.Default.PictureInPictureAlt))
        }
    }
    fun grants() = options.map { option ->
        if (option.permission == Manifest.permission.SYSTEM_ALERT_WINDOW) Settings.canDrawOverlays(context)
        else ContextCompat.checkSelfPermission(context, option.permission) == PackageManager.PERMISSION_GRANTED
    }
    var allowed by remember(context, options) { mutableStateOf(grants()) }
    var requesting by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = grants()
        requesting = false
    }
    val overlaySettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { allowed = grants() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allowed = grants() }

    SettingsGroup(uiText("onboarding.permission_group"), uiText("onboarding.permission_scope")) {
        options.forEachIndexed { index, option ->
            val granted = allowed[index]
            val position = when (index) {
                0 -> SettingPosition.FIRST
                options.lastIndex -> SettingPosition.LAST
                else -> SettingPosition.MIDDLE
            }
            SettingItem(uiText(option.title), uiText(option.detail), option.icon, position,
                modifier = Modifier.testTag("onboarding-permission-${option.permission}"),
                enabled = !granted && !requesting, onClick = {
                    if (option.permission == Manifest.permission.SYSTEM_ALERT_WINDOW) {
                        val settings = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                        runCatching { overlaySettings.launch(Intent(settings).setData(Uri.parse("package:${context.packageName}"))) }
                            .recoverCatching { overlaySettings.launch(settings) }
                    } else {
                        requesting = true
                        launcher.launch(option.permission)
                    }
                }, trailing = { Text(uiText(if (granted) "onboarding.permission_granted" else "permission.allow")) })
        }
    }
}
