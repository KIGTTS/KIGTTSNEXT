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
import com.kigtts.plugin.runtime.PluginPermissions

/** Capability identity stays visible even when an app or purpose has a long name. */
@Composable
internal fun PermissionDialogTitle(prompt: PermissionPrompt) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("允许${prompt.name}${PluginPermissions.label(prompt.permission)}？", Modifier.weight(1f))
        Icon(permissionIcon(prompt.permission), PluginPermissions.label(prompt.permission),
            Modifier.size(28.dp).testTag("permission-icon/${prompt.permission}"), tint = MaterialTheme.colorScheme.primary)
    }
}

internal fun permissionIcon(permission: String): ImageVector = when (permission) {
    "network" -> Icons.Default.Language
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
