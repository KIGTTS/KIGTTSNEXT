package com.kigtts.next.ui.files

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*

internal fun directoryCrumbs(directory: String): List<Pair<String, String>> = buildList {
    add("" to "")
    var path = ""
    directory.split('/').filter(String::isNotEmpty).forEach { name -> path = if (path.isEmpty()) name else "$path/$name"; add(name to path) }
}
internal fun folderDestinationAllowed(paths: List<String>, directory: String) =
    paths.none { it == directory || directory.startsWith("$it/") }

@Composable
internal fun DirectoryBreadcrumbs(directory: String, enabled: Boolean, navigate: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(48.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        directoryCrumbs(directory).forEachIndexed { index, (name, path) ->
            if (index > 0) Text("/", color = MaterialTheme.colorScheme.onSurfaceVariant)
            MotionTextButton(feedbackClick { navigate(path) }, enabled = enabled && path != directory,
                modifier = Modifier.testTag("folder-crumb/$path")) { Text(if (path.isBlank()) uiText("file.root") else name, maxLines = 1) }
        }
    }
}

@Composable
internal fun FileSelectionBar(selected: List<String>, total: Int, enabled: Boolean, all: () -> Unit, cancel: () -> Unit,
    move: () -> Unit, copy: () -> Unit, delete: () -> Unit, emptyActions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(56.dp).testTag("file-selection-bar"), verticalAlignment = Alignment.CenterVertically) {
        Text(if (selected.isEmpty()) uiText("file.title") else uiText("file.selected_count", "count" to selected.size.toString()), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        if (selected.isNotEmpty()) {
            MotionIconButton(feedbackClick(move), enabled = enabled) { Icon(Icons.Default.DriveFileMove, uiText("file.move_selected")) }
            MotionIconButton(feedbackClick(copy), enabled = enabled) { Icon(Icons.Default.ContentCopy, uiText("file.copy_selected")) }
            MotionIconButton(feedbackClick(delete), enabled = enabled) { Icon(Icons.Default.Delete, uiText("file.delete_selected_action")) }
            MotionIconButton(feedbackClick(cancel), enabled = enabled) { Icon(Icons.Default.Close, uiText("file.cancel_selection")) }
        }
        else emptyActions()
        MotionIconButton(feedbackClick(all), enabled = enabled && total > 0) { Icon(Icons.Default.SelectAll, uiText("file.select_all")) }
    }
}
