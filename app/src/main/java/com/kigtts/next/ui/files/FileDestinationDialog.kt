package com.kigtts.next.ui.files

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Destination navigation is confined to folders. Breadcrumbs and cancellation always remain reachable. */
@Composable
internal fun FileDestinationDialog(title: String, directory: String, folders: List<BrowserFile>, paths: List<String>,
    enabled: Boolean, navigate: (String) -> Unit, confirm: (String, () -> Unit) -> Unit, close: () -> Unit, error: String? = null,
    confirmedClose: () -> Unit = close) {
    MotionAlertDialog(onDismissRequest = close, title = { Text(title) }, text = {
        Column {
            DirectoryBreadcrumbs(directory, enabled, navigate)
            FileBrowserRows(folders.filter { it.directory }, emptyList(), enabled, {}, { navigate(it.path) }, { _, _ -> },
                Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp), tagPrefix = "destination-folder", showMenu = false,
                emptyText = "没有子文件夹")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = {
        val finish = rememberDialogClose()
        MotionTextButton(feedbackClick { confirm(directory) { finish(confirmedClose) } }, enabled = enabled && folderDestinationAllowed(paths, directory) &&
            (title.startsWith("复制") || paths.any { it.substringBeforeLast('/', "") != directory })) {
            Text(if (title.startsWith("复制")) "复制到这里" else "移动到这里")
        }
    }, dismissButton = { MotionTextButton(dialogFeedbackClick(close)) { Text("取消") } })
}

@Composable
internal fun LocalFileDestinationDialog(title: String, initial: String, paths: List<String>,
    load: suspend (String) -> List<BrowserFile>, confirm: (String, () -> Unit) -> Unit, close: () -> Unit,
    enabled: Boolean = true, operationError: String? = null) {
    var directory by remember { mutableStateOf(initial) }
    var folders by remember { mutableStateOf(emptyList<BrowserFile>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(directory) {
        loading = true; error = null
        try { folders = withContext(Dispatchers.IO) { load(directory) } }
        catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (failure: Exception) { error = failure.message ?: "无法打开文件夹"; folders = emptyList() }
        finally { loading = false }
    }
    FileDestinationDialog(title, directory, folders, paths, enabled && !loading, { directory = it },
        confirm, close, operationError ?: error)
}
