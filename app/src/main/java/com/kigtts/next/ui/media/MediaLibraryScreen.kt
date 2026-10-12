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
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.next.ui.components.dialogFeedbackClick
import com.kigtts.next.ui.components.rememberDialogClose
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
    val phrases by model.phrases.collectAsStateWithLifecycle()
    val phraseDraft by model.content.phraseDraft.collectAsStateWithLifecycle()
    val pagesOnly = target.startsWith("phrase:") && phraseDraft?.type == QuickCardType.PAGE
    val shown = if (pagesOnly) entries.filter { it.kind in setOf(MediaKind.HTML, MediaKind.WEB) } else entries
    val state by model.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope(); val active = LocalPageActive.current
    var importing by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf<MediaEntry?>(null) }; var deleting by remember { mutableStateOf<MediaEntry?>(null) }
    var pairing by remember { mutableStateOf<String?>(null) }; var website by remember { mutableStateOf(false) }
    var framing by remember { mutableStateOf<MediaEntry?>(null) }
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
    RegisterPageActions(listOfNotNull(
        if (pagesOnly) null else ToolbarAction("import-media","导入图片或视频",Icons.Default.AddPhotoAlternate,!importing) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
        ToolbarAction("import-media-file","导入文件",Icons.Default.FileOpen,!importing) { document.launch(if (pagesOnly) arrayOf("text/html","application/xhtml+xml") else arrayOf("text/html","application/xhtml+xml","image/*","video/*")) },
        ToolbarAction("create-html","新建 HTML",Icons.Default.Code) { model.content.editor(html=true,target=target) },
        ToolbarAction("add-webpage","添加网页",Icons.Default.Language) { website=true }
    ))
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (shown.isEmpty()) Text(if (pagesOnly) "编写 HTML 或添加网页后即可使用。" else "导入后点击媒体即可使用。Android 动态照片会自动识别。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyVerticalGrid(GridCells.Adaptive(148.dp), modifier = Modifier.weight(1f).testTag("media-library"),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(shown, key = { it.id }) { entry ->
                Surface(onClick = feedbackClick { model.content.choose(entry, target) }, shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.testTag("media-select-" + entry.id)) {
                    Box(Modifier.fillMaxWidth().height(148.dp)) {
                        MediaCardBackdrop(entry, model.mediaRepository, Modifier.matchParentSize(), MaterialTheme.colorScheme.surfaceContainer)
                        Column(Modifier.fillMaxHeight().fillMaxWidth(.6f).padding(12.dp), verticalArrangement = Arrangement.Center) {
                        Text(mediaDisplayTitle(entry,phrases), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (entry.appleLive) "苹果实况照片" else entry.kind.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FilledTonalIconButton(feedbackClick { menu = entry }, Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp).testTag("manage-media-" + entry.id)) {
                            Icon(Icons.Default.MoreVert, "管理 " + mediaDisplayTitle(entry,phrases), Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
    if (active) menu?.let { entry -> MotionAlertDialog(onDismissRequest = { menu = null }, title = { Text(mediaDisplayTitle(entry,phrases)) }, text = { Column {
        TextButton(dialogFeedbackClick { menu = null; framing = entry }, Modifier.testTag("edit-thumbnail-framing")) { Text("缩略图取景") }
        if (state.appleLivePhotos && entry.pluginId == null && entry.kind in setOf(MediaKind.IMAGE, MediaKind.MOTION_PHOTO)) TextButton(dialogFeedbackClick {
            menu = null; pairing = entry.id; pair.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        }, Modifier.testTag("pair-live-photo")) { Text("配对苹果实况视频") }
        if (entry.kind == MediaKind.HTML && entry.pluginId == null) TextButton(dialogFeedbackClick { menu = null; model.content.editor(html = true, target = target, selectedMedia = entry.id) }) { Text("编辑 HTML") }
        TextButton(dialogFeedbackClick { menu = null; deleting = entry }) { Text("删除", color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { TextButton(dialogFeedbackClick { menu = null }) { Text("关闭") } }) }
    if (active) deleting?.let { entry -> MotionAlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除媒体？") },
        text = { Text("引用此媒体的快捷条目将保留文字并移除媒体关联。") }, confirmButton = { val closeDialog = rememberDialogClose(); TextButton(feedbackClick {
            scope.launch { try { model.content.removeAsync(entry.id); closeDialog { deleting = null } } catch (e: Exception) { error = e.message } }
        }) { Text("删除") } }, dismissButton = { TextButton(dialogFeedbackClick { deleting = null }) { Text("取消") } }) }
    if (active && website) WebsiteDialog(model, close = { website = false })
    if (active) framing?.let { ThumbnailFramingDialog(it, model.mediaRepository) { framing = null } }
}

@Composable
private fun WebsiteDialog(model: AppViewModel, close: () -> Unit) {
    var url by remember { mutableStateOf("https://") }; var title by remember { mutableStateOf("") }; var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    MotionAlertDialog(onDismissRequest = close, title = { Text("添加网页") }, text = { Column {
        OutlinedTextField(url, { url = it }, label = { Text("HTTPS 地址") }); OutlinedTextField(title, { title = it }, label = { Text("名称（可选）") })
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { val closeDialog = rememberDialogClose(); TextButton(feedbackClick { scope.launch { try { withContext(Dispatchers.IO) { model.mediaRepository.web(title, url) }; closeDialog(close) } catch (e: Exception) { error = e.message } } }) { Text("添加") } },
        dismissButton = { TextButton(dialogFeedbackClick(close)) { Text("取消") } })
}
