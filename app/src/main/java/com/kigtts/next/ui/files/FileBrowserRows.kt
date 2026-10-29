package com.kigtts.next.ui.files

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.MotionIconButton
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.EmptyContent

internal data class BrowserFile(val path: String, val name: String, val directory: Boolean,
    val summary: String = "", val icon: ImageVector = if (directory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile)
internal enum class FileAction(val label: String, val icon: ImageVector) {
    MOVE("移动到…", Icons.Default.DriveFileMove), COPY("复制到…", Icons.Default.ContentCopy),
    RENAME("重命名", Icons.Default.Edit), DELETE("删除", Icons.Default.Delete),
}
internal data class FileMenuAction(val label: String, val icon: ImageVector, val click: () -> Unit)

/** A fixed trailing cell and a reserved subtitle line keep selection/menu swaps from changing row height. */
@Composable
internal fun FileBrowserRows(items: List<BrowserFile>, selected: List<String>, enabled: Boolean,
    select: (String) -> Unit, open: (BrowserFile) -> Unit, action: (BrowserFile, FileAction) -> Unit,
    modifier: Modifier = Modifier, tagPrefix: String = "file", extra: (BrowserFile) -> List<FileMenuAction> = { emptyList() },
    showMenu: Boolean = true, emptyText: String = "这里还没有文件") {
    Box(modifier) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(items, key = { it.path }) { item ->
            val selectedColor by animateColorAsState(if (item.path in selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainer, tween(120), label = "file-selection")
            Surface(Modifier.animateItem().testTag("$tagPrefix/${item.path}"), shape = MaterialTheme.shapes.medium, color = selectedColor) {
                Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).combinedClickable(enabled = enabled,
                    onLongClick = if (showMenu) ({ select(item.path) }) else null, onClick = feedbackClick {
                        if (selected.isNotEmpty()) select(item.path) else open(item)
                    }).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(item.icon, null, Modifier.size(24.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item.summary.ifBlank { " " }, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        if (!showMenu) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                        else {
                        Crossfade(selected.isNotEmpty(), animationSpec = tween(120), label = "file-trailing") { selecting ->
                            if (selecting) Checkbox(item.path in selected, null, modifier = Modifier.size(48.dp))
                            else FileRowMenu(item, enabled && selected.isEmpty(), tagPrefix, action, extra(item))
                        }
                        }
                    }
                }
            }
        }
    }
    EmptyContent(items.isEmpty(), Icons.Default.FolderOpen, emptyText)
    }
}

@Composable
private fun FileRowMenu(item: BrowserFile, enabled: Boolean, tagPrefix: String,
    action: (BrowserFile, FileAction) -> Unit, extra: List<FileMenuAction>) {
    var expanded by remember(item.path) { mutableStateOf(false) }
    Box(Modifier.size(48.dp)) {
        MotionIconButton(feedbackClick { expanded = true }, Modifier.size(48.dp).testTag("$tagPrefix-menu/${item.path}"), enabled = enabled) {
            Icon(Icons.Default.MoreVert, "文件操作")
        }
        DropdownMenu(expanded && enabled, { expanded = false }) {
            extra.forEach { item -> DropdownMenuItem({ Text(item.label) }, feedbackClick { expanded = false; item.click() },
                leadingIcon = { Icon(item.icon, null) }) }
            FileAction.entries.forEach { operation -> DropdownMenuItem({ Text(operation.label) },
                feedbackClick { expanded = false; action(item, operation) }, leadingIcon = { Icon(operation.icon, null) }) }
        }
    }
}

internal fun fileSizeSummary(bytes: Long) = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(java.util.Locale.ROOT, bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KB".format(java.util.Locale.ROOT, bytes / 1024.0)
    else -> "$bytes B"
}
