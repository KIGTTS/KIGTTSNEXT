package com.kigtts.next.ui.plugins

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.plugin.api.ChatFile
import java.util.Locale

@Composable internal fun ChatFiles(files: List<ChatFile>, invoke: ((String, Map<String, String>) -> Unit)? = null,
    remove: ((ChatFile) -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        files.forEach { file ->
            Surface(onClick = feedbackClick { file.openAction?.let { invoke?.invoke(it, emptyMap()) } },
                enabled = file.openAction != null && invoke != null,
                shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.widthIn(max = 256.dp).testTag("chat-file/${file.source.path}")) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.InsertDriveFile, null, Modifier.size(24.dp))
                    Column(Modifier.weight(1f, fill = false)) {
                        Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                        Text(chatFileSize(file.bytes), style = MaterialTheme.typography.labelSmall)
                    }
                    if (remove != null) IconButton(onClick = feedbackClick { remove(file) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, uiText("accessibility.remove_file", "name" to file.name), Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

internal fun chatFileSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024))
    bytes >= 1024 -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
    else -> "${bytes.coerceAtLeast(0)} B"
}
