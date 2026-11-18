package com.kigtts.next.ui.media

import com.kigtts.next.i18n.uiText

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.media.*
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.files.*
import com.kigtts.next.ui.navigation.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A project browser with shared stable rows and explicit move/copy destination navigation. */
@Composable
fun HtmlFilesScreen(model: AppViewModel, id: String, target: String) {
    val files = model.mediaRepository.htmlFiles
    val scope = rememberCoroutineScope(); val active = LocalPageActive.current
    var directory by rememberSaveable(id) { mutableStateOf("") }
    var selected by rememberSaveable(id) { mutableStateOf(emptyList<String>()) }
    var items by remember(id) { mutableStateOf(emptyList<HtmlProjectItem>()) }
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    var command by remember { mutableStateOf<HtmlFileCommand?>(null) }
    var transfer by remember { mutableStateOf<HtmlFileTransfer?>(null) }
    var preview by remember { mutableStateOf<HtmlProjectItem?>(null) }
    var createMenu by remember { mutableStateOf(false) }
    fun work(action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try { action(); revision++ }
            catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { error = failure.message ?: "操作失败" }
            finally { busy = false }
        }
    }
    fun navigate(path: String) { directory = path; selected = emptyList() }
    fun chooseTransfer(paths: List<String>, copy: Boolean) { error = null; transfer = HtmlFileTransfer(paths.toList(), copy) }
    fun browser(item: HtmlProjectItem) = BrowserFile(item.path, item.name, item.directory,
        if (item.main) "主页面" else if (item.directory) "文件夹" else fileSizeSummary(item.size))
    LaunchedEffect(id, directory, revision) {
        try { items = withContext(Dispatchers.IO) { files.list(id, directory) } }
        catch (cancel: CancellationException) { throw cancel }
        catch (failure: Exception) { error = failure.message ?: "无法打开文件夹" }
    }
    BackHandler(active && selected.isNotEmpty() && command == null && transfer == null && preview == null) { selected = emptyList() }
    RegisterPageLoading(busy)
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) work {
        withContext(Dispatchers.IO) { model.mediaRepository.htmlImports.documents(id, directory, uris) }
    } }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) work {
        withContext(Dispatchers.IO) { model.mediaRepository.htmlImports.tree(id, directory, uri) }
    } }
    RegisterPageActions(listOf(
        ToolbarAction("html-preview", "预览页面", Icons.Default.PlayArrow, !busy) { model.navigate(Destination.HtmlPreview(id)) },
        ToolbarAction("html-import-folder", "导入文件夹", Icons.Default.FolderOpen, !busy) { folder.launch(null) },
    ))
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("html-files")) {
        DirectoryBreadcrumbs(directory, active && !busy, ::navigate)
        FileSelectionBar(selected, items.size, active && !busy,
            { selected = if (selected.size == items.size) emptyList() else items.map { it.path } }, { selected = emptyList() },
            { chooseTransfer(selected, false) }, { chooseTransfer(selected, true) }, { command = HtmlFileCommand.Delete(selected.toList()) }) {
            MotionIconButton(feedbackClick { importer.launch(arrayOf("*/*")) }, enabled = active && !busy,
                modifier = Modifier.testTag("html-import")) {
                Icon(Icons.Default.FileOpen, "批量导入文件或媒体")
            }
            Box {
                MotionIconButton(feedbackClick { createMenu = true }, enabled = active && !busy) { Icon(Icons.Default.Add, "新建文件或文件夹") }
                DropdownMenu(createMenu && active, { createMenu = false }) {
                    DropdownMenuItem({ Text(uiText("editor.new_file")) }, feedbackClick { createMenu = false; command = HtmlFileCommand.Create(false) })
                    DropdownMenuItem({ Text(uiText("editor.new_folder")) }, feedbackClick { createMenu = false; command = HtmlFileCommand.Create(true) })
                }
            }
        }
        error?.takeIf { command == null && transfer == null }?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }
        FileBrowserRows(items.map(::browser), selected, active && !busy,
            { path -> selected = if (path in selected) selected - path else selected + path },
            { item ->
                val source = items.first { it.path == item.path }
                if (item.directory) navigate(item.path)
                else if (source.editable) work { model.content.editorFile(id, item.path, target) }
                else preview = source
            }, { item, action ->
                val source = items.first { it.path == item.path }
                when (action) {
                    FileAction.MOVE -> chooseTransfer(listOf(item.path), false)
                    FileAction.COPY -> chooseTransfer(listOf(item.path), true)
                    FileAction.RENAME -> { error = null; command = HtmlFileCommand.Rename(source) }
                    FileAction.DELETE -> { error = null; command = HtmlFileCommand.Delete(listOf(item.path)) }
                }
            }, Modifier.weight(1f), tagPrefix = "html-file", extra = { item ->
                if (!item.directory && item.name.substringAfterLast('.').lowercase() in setOf("html", "htm"))
                    listOf(FileMenuAction("设为主页面", Icons.Default.Home) { work { withContext(Dispatchers.IO) { files.selectMain(id, item.path) } } })
                else emptyList()
            })
    }
    if (active) transfer?.let { request -> LocalFileDestinationDialog(if (request.copy) "复制文件" else "移动文件", directory,
        request.paths, { path -> files.list(id, path).filter { it.directory }.map(::browser) },
        { destination, finish -> work {
            withContext(Dispatchers.IO) {
                require(files.file(id, destination).isDirectory) { "目标文件夹已不存在" }
                require(request.paths.all { files.file(id, it).exists() }) { "所选文件已移动或删除，请重新选择" }
                files.move(id, request.paths, destination, request.copy)
            }
            finish(); selected = emptyList()
        } }, { transfer = null; error = null }, enabled = !busy, operationError = error) }
    if (active) command?.let { action -> HtmlFileDialog(action, busy, error, { command = null; error = null }) { name, finish ->
        work {
            withContext(Dispatchers.IO) { when (action) {
                is HtmlFileCommand.Create -> files.create(id, directory, name, action.folder)
                is HtmlFileCommand.Rename -> files.rename(id, action.item.path, name)
                is HtmlFileCommand.Delete -> files.delete(id, action.paths)
            } }
            finish(); selected = emptyList()
        }
    } }
    if (active) preview?.let { item -> HtmlFilePreview(item, model.mediaRepository, id) { preview = null } }
}
