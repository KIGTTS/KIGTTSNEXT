package com.kigtts.next.ui.plugins

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.plugin.runtime.PermissionPrompt
import com.kigtts.next.i18n.uiText

/** Capability identity stays visible even when an app or purpose has a long name. */
@Composable
internal fun PermissionDialogTitle(prompt: PermissionPrompt) {
    val label = pluginPermissionLabel(prompt.permission)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(uiText("permission.dialog", "plugin" to prompt.name, "permission" to label), Modifier.weight(1f))
        Icon(permissionIcon(prompt.permission), label,
            Modifier.size(28.dp).testTag("permission-icon/${prompt.permission}"), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable internal fun pluginPermissionLabel(permission: String): String = when (permission) {
    "tts", "speech-service" -> uiText("permission.tts")
    "asr-batch", "asr-segmented", "asr-incremental", "asr-service" -> uiText("permission.asr")
    "network", "app-control", "quick-text-read", "notifications", "overlay", "quick-text-write", "stickers-read",
    "stickers-write", "voice-packs", "camera", "microphone", "motion-sensors", "downloads", "web-presentation" -> uiText("permission.$permission")
    else -> permission
}

internal fun permissionIcon(permission: String): ImageVector = when (permission) {
    "network" -> Icons.Default.Language
    "notifications" -> Icons.Default.NotificationsActive
    "overlay" -> Icons.Default.BubbleChart
    "app-control" -> Icons.Default.TouchApp
    "quick-text-read" -> Icons.AutoMirrored.Filled.Article
    "quick-text-write" -> Icons.Default.EditNote
    "stickers-read" -> Icons.Default.PhotoLibrary
    "stickers-write" -> Icons.Default.AddPhotoAlternate
    "tts", "speech-service" -> Icons.AutoMirrored.Filled.VolumeUp
    "asr-batch", "asr-segmented", "asr-incremental", "asr-service", "microphone" -> Icons.Default.Mic
    "voice-packs" -> Icons.Default.LibraryMusic
    "camera" -> Icons.Default.PhotoCamera
    "motion-sensors" -> Icons.Default.Sensors
    "downloads" -> Icons.Default.Download
    else -> Icons.Default.Shield
}
