package com.kigtts.next.ui.media

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.media.*
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.navigation.LocalPageActive
import com.kigtts.next.ui.navigation.RegisterPageActions
import com.kigtts.next.ui.navigation.ToolbarAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MediaLibraryScreen(model: AppViewModel, target: String) {
    val entries by model.mediaRepository.entries.collectAsStateWithLifecycle()
    val state by model.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope(); val active = LocalPageActive.current
    var importing by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf<MediaEntry?>(null) }; var deleting by remember { mutableStateOf<MediaEntry?>(null) }
    var pairing by remember { mutableStateOf<String?>(null) }; var website by remember { mutableStateOf(false) }
    fun import(uri: android.net.Uri?) { if (uri == null) return
        scope.launch { importing = true; error = null
            try { withContext(Dispatchers.IO) { model.mediaRepository.import(uri) } }
            catch (e: Exception) { error = e.message ?: "导入失败" } finally { importing = false }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), ::import)
    val document = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), ::import)
    val pair = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val id = pairing; pairing = null
        if (uri != null && id != null && model.state.value.appleLivePhotos) scope.launch {
            importing = true
            try { withContext(Dispatchers.IO) { model.mediaRepository.pair(id, uri) } }
            catch (e: Exception) { error = e.message ?: "配对失败" } finally { importing = false }
        }
    }
    RegisterPageActions(listOf(
        ToolbarAction("import-media","导入图片或视频",Icons.Default.AddPhotoAlternate,!importing) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
        ToolbarAction("import-media-file","导入文件",Icons.Default.FileOpen,!importing) { document.launch(arrayOf("text/html","application/xhtml+xml","image/*","video/*")) },
        ToolbarAction("create-html","新建 HTML",Icons.Default.Code) { model.content.editor(html=true,target=target) },
        ToolbarAction("add-webpage","添加网页",Icons.Default.Language) { website=true }
    ))
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (entries.isEmpty()) Text("导入后点击媒体即可使用。Android 动态照片会自动识别。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyVerticalGrid(GridCells.Adaptive(148.dp), modifier = Modifier.weight(1f).testTag("media-library"),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(entries, key = { it.id }) { entry ->
                Surface(onClick = feedbackClick { model.content.choose(entry, target) }, shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.testTag("media-select-" + entry.id)) {
                    Column(Modifier.padding(10.dp)) {
                        Box(Modifier.fillMaxWidth().height(112.dp), contentAlignment = Alignment.Center) {
                            if (entry.kind in setOf(MediaKind.IMAGE, MediaKind.MOTION_PHOTO)) MediaImage(model.mediaRepository.file(entry), entry.name, Modifier.fillMaxSize(), 512)
                            else Icon(if (entry.kind == MediaKind.VIDEO) Icons.Default.Movie else Icons.Default.Language, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                            IconButton(feedbackClick { menu = entry }, Modifier.align(Alignment.TopEnd)) { Icon(Icons.Default.MoreVert, "管理 " + entry.name) }
                        }
                        Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (entry.appleLive) "苹果实况照片" else entry.kind.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (active) menu?.let { entry -> AlertDialog(onDismissRequest = { menu = null }, title = { Text(entry.name) }, text = { Column {
        if (state.appleLivePhotos && entry.pluginId == null && entry.kind in setOf(MediaKind.IMAGE, MediaKind.MOTION_PHOTO)) TextButton(feedbackClick {
            menu = null; pairing = entry.id; pair.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        }, Modifier.testTag("pair-live-photo")) { Text("配对苹果实况视频") }
        if (entry.kind == MediaKind.HTML && entry.pluginId == null) TextButton(feedbackClick { menu = null; model.content.editor(html = true, target = target, selectedMedia = entry.id) }) { Text("编辑 HTML") }
        TextButton(feedbackClick { menu = null; deleting = entry }) { Text("删除", color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { TextButton(feedbackClick { menu = null }) { Text("关闭") } }) }
    if (active) deleting?.let { entry -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除媒体？") },
        text = { Text("引用此媒体的快捷条目将保留文字并移除媒体关联。") }, confirmButton = { TextButton(feedbackClick {
            scope.launch { try { withContext(Dispatchers.IO) { model.content.remove(entry.id) }; deleting = null } catch (e: Exception) { error = e.message } }
        }) { Text("删除") } }, dismissButton = { TextButton(feedbackClick { deleting = null }) { Text("取消") } }) }
    if (active && website) WebsiteDialog(model, close = { website = false })
}

@Composable
private fun WebsiteDialog(model: AppViewModel, close: () -> Unit) {
    var url by remember { mutableStateOf("https://") }; var title by remember { mutableStateOf("") }; var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = close, title = { Text("添加网页") }, text = { Column {
        OutlinedTextField(url, { url = it }, label = { Text("HTTPS 地址") }); OutlinedTextField(title, { title = it }, label = { Text("名称（可选）") })
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { TextButton(feedbackClick { scope.launch { try { withContext(Dispatchers.IO) { model.mediaRepository.web(title, url) }; close() } catch (e: Exception) { error = e.message } } }) { Text("添加") } },
        dismissButton = { TextButton(feedbackClick(close)) { Text("取消") } })
}
