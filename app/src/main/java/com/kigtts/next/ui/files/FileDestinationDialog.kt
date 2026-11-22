package com.kigtts.next.ui.files

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class FolderPage(val directory: String, val folders: List<BrowserFile>?)

/** Destination navigation is confined to folders. Breadcrumbs and cancellation always remain reachable. */
@Composable
internal fun FileDestinationDialog(copying: Boolean, directory: String, folders: List<BrowserFile>?, paths: List<String>,
    enabled: Boolean, navigate: (String) -> Unit, confirm: (String, () -> Unit) -> Unit, close: () -> Unit, error: String? = null,
    confirmedClose: () -> Unit = close) {
    MotionAlertDialog(onDismissRequest = close, title = { Text(uiText(if (copying) "file.copy_title" else "file.move_title")) }, text = {
        Column {
            DirectoryBreadcrumbs(directory, enabled, navigate)
            ModalPageTransition(FolderPage(directory, folders), { directoryCrumbs(it.directory).size },
                Modifier.fillMaxWidth(), pageKey = { it.directory }) { page ->
                FileBrowserRows(page.folders.orEmpty().filter { it.directory }, emptyList(), enabled, {}, { navigate(it.path) }, { _, _ -> },
                    Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp), tagPrefix = "destination-folder", showMenu = false,
                    emptyText = uiText(if (page.folders == null) "file.opening" else "file.no_subfolders"))
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = {
        val finish = rememberDialogClose()
        MotionTextButton(feedbackClick { confirm(directory) { finish(confirmedClose) } }, enabled = enabled && folderDestinationAllowed(paths, directory) &&
            (copying || paths.any { it.substringBeforeLast('/', "") != directory })) {
            Text(uiText(if (copying) "file.copy_here" else "file.move_here"))
        }
    }, dismissButton = { MotionTextButton(dialogFeedbackClick(close)) { Text(uiText("action.cancel")) } })
}

@Composable
internal fun LocalFileDestinationDialog(copying: Boolean, initial: String, paths: List<String>,
    load: suspend (String) -> List<BrowserFile>, confirm: (String, () -> Unit) -> Unit, close: () -> Unit,
    enabled: Boolean = true, operationError: String? = null) {
    var directory by remember { mutableStateOf(initial) }
    var folders by remember(directory) { mutableStateOf<List<BrowserFile>?>(null) }
    var error by remember(directory) { mutableStateOf<String?>(null) }
    val openFailed = uiText("file.open_folder_failed")
    LaunchedEffect(directory) {
        try { folders = withContext(Dispatchers.IO) { load(directory) } }
        catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (failure: Exception) { error = openFailed; folders = emptyList() }
    }
    FileDestinationDialog(copying, directory, folders, paths, enabled && folders != null, { directory = it },
        confirm, close, operationError ?: error)
}
