package com.kigtts.next.ui.media

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.media.*
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.navigation.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A small project browser: selecting files never changes the subtitle until Preview is requested. */
@Composable
fun HtmlFilesScreen(model: AppViewModel, id: String, target: String) {
    val files=model.mediaRepository.htmlFiles;val scope=rememberCoroutineScope();val active=LocalPageActive.current
    var directory by rememberSaveable(id) { mutableStateOf("") }
    var selected by rememberSaveable(id) { mutableStateOf(emptyList<String>()) }
    var clipboard by rememberSaveable(id) { mutableStateOf(emptyList<String>()) };var copy by rememberSaveable(id) { mutableStateOf(false) }
    var items by remember(id) { mutableStateOf(emptyList<HtmlProjectItem>()) };var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) };var error by remember { mutableStateOf<String?>(null) }
    var command by remember { mutableStateOf<HtmlFileCommand?>(null) };var preview by remember { mutableStateOf<HtmlProjectItem?>(null) }
    fun work(action:suspend ()->Unit) { if(busy)return;busy=true;error=null;scope.launch {
        try { action();revision++ } catch(e:Exception) { error=e.message ?: "操作失败" } finally { busy=false }
    } }
    LaunchedEffect(id,directory,revision) {
        try { items=withContext(Dispatchers.IO) { files.list(id,directory) } } catch(e:Exception) { error=e.message }
    }
    RegisterPageLoading(busy)
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if(uris.isNotEmpty())work {
        withContext(Dispatchers.IO) { model.mediaRepository.htmlImports.documents(id,directory,uris) }
    } }
    val folder=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if(uri!=null)work {
        withContext(Dispatchers.IO) { model.mediaRepository.htmlImports.tree(id,directory,uri) }
    } }
    RegisterPageActions(listOf(
        ToolbarAction("html-preview","预览页面",Icons.Default.PlayArrow,!busy) { model.navigate(Destination.HtmlPreview(id)) },
        ToolbarAction("html-import","批量导入文件或媒体",Icons.Default.FileOpen,!busy) { import.launch(arrayOf("*/*")) },
        ToolbarAction("html-import-folder","导入文件夹",Icons.Default.FolderOpen,!busy) { folder.launch(null) },
        ToolbarAction("html-new-file","新建文件",Icons.AutoMirrored.Filled.NoteAdd,!busy) { command=HtmlFileCommand.Create(false) },
        ToolbarAction("html-new-folder","新建文件夹",Icons.Default.CreateNewFolder,!busy) { command=HtmlFileCommand.Create(true) }
    ))
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp).testTag("html-files")) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            MotionIconButton(feedbackClick { directory=directory.substringBeforeLast('/',"");selected=emptyList() },enabled=directory.isNotEmpty()&&!busy) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack,"上一级文件夹")
            }
            Text(if(directory.isEmpty())"页面根目录" else directory.substringAfterLast('/'),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            MotionTextButton(feedbackClick { selected=if(selected.size==items.size)emptyList() else items.map { it.path } },enabled=!busy) { Text(if(selected.isEmpty())"选择" else "全选") }
        }
        AnimatedFileSelection(selected,clipboard.isNotEmpty(),busy,{ copy=true;clipboard=selected;selected=emptyList() },
            { copy=false;clipboard=selected;selected=emptyList() },{ command=HtmlFileCommand.Delete(selected) },{
                work { withContext(Dispatchers.IO) { files.move(id,clipboard,directory,copy) };clipboard=emptyList();selected=emptyList() }
            },{ selected=emptyList();clipboard=emptyList() })
        error?.let { Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(8.dp)) }
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            items(items,key={it.path}) { item ->
                Surface(Modifier.animateItem().testTag("html-file/"+item.path),shape=MaterialTheme.shapes.medium,
                    color=if(item.path in selected)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.fillMaxWidth().combinedClickable(enabled=active&&!busy,onLongClick=feedbackClick {
                        selected=if(item.path in selected)selected-item.path else selected+item.path
                    },onClick=feedbackClick {
                        if(selected.isNotEmpty())selected=if(item.path in selected)selected-item.path else selected+item.path
                        else if(item.directory)directory=item.path
                        else if(item.editable)work { model.content.editorFile(id,item.path,target) } else preview=item
                    }).padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                        Icon(if(item.directory)Icons.Default.Folder else if(item.editable)Icons.Default.Code else Icons.AutoMirrored.Filled.InsertDriveFile,null)
                        Column(Modifier.weight(1f).padding(horizontal=12.dp)) {
                            Text(item.name,style=MaterialTheme.typography.titleMedium)
                            if(item.main)Text("主页面",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
                        }
                        if(selected.isNotEmpty())Checkbox(item.path in selected,null)
                        else HtmlFileMenu(item,active&&!busy,{ command=it },{ work { withContext(Dispatchers.IO) { files.selectMain(id,item.path) } } })
                    }
                }
            }
            if(items.isEmpty())item { Text("这里还没有文件",Modifier.padding(24.dp),color=MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    if(active)command?.let { action -> HtmlFileDialog(action,busy,error,{command=null;error=null}) { name,finish ->
        work { withContext(Dispatchers.IO) { when(action) {
            is HtmlFileCommand.Create -> files.create(id,directory,name,action.folder)
            is HtmlFileCommand.Rename -> files.rename(id,action.item.path,name)
            is HtmlFileCommand.Delete -> files.delete(id,action.paths)
        } };finish();selected=emptyList() }
    } }
    if(active)preview?.let { item -> HtmlFilePreview(item,model.mediaRepository,id) { preview=null } }
}
