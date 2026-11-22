package com.kigtts.next.ui.media

import com.kigtts.next.i18n.uiText

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
import com.kigtts.next.ui.components.EmptyContent
import com.kigtts.next.ui.navigation.LocalPageActive
import com.kigtts.next.ui.navigation.RegisterPageActions
import com.kigtts.next.ui.navigation.ToolbarAction
import kotlinx.coroutines.CancellationException
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
    val importFailed = uiText("media.import_failed")
    val pairFailed = uiText("media.pair_failed")
    var importing by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf<MediaEntry?>(null) }; var deleting by remember { mutableStateOf<MediaEntry?>(null) }
    var pairing by remember { mutableStateOf<String?>(null) }; var website by remember { mutableStateOf(false) }
    var framing by remember { mutableStateOf<MediaEntry?>(null) }
    fun import(uri: android.net.Uri?) { if (uri == null) return
        scope.launch { importing = true; error = null
            try { withContext(Dispatchers.IO) { model.mediaRepository.import(uri) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: importFailed } finally { importing = false }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), ::import)
    val document = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), ::import)
    val pair = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val id = pairing; pairing = null
        if (uri != null && id != null && model.state.value.appleLivePhotos) scope.launch {
            importing = true
            try { withContext(Dispatchers.IO) { model.mediaRepository.pair(id, uri) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: pairFailed } finally { importing = false }
        }
    }
    RegisterPageActions(listOfNotNull(
        if (pagesOnly) null else ToolbarAction("import-media",uiText("media.import_image_video"),Icons.Default.AddPhotoAlternate,!importing) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
        ToolbarAction("import-media-file",uiText("file.import"),Icons.Default.FileOpen,!importing) { document.launch(if (pagesOnly) arrayOf("text/html","application/xhtml+xml") else arrayOf("text/html","application/xhtml+xml","image/*","video/*")) },
        ToolbarAction("create-html",uiText("media.new_webpage"),Icons.Default.Code) { scope.launch { runCatching { model.content.htmlFiles(target) }.onFailure { error=it.message } } },
        ToolbarAction("add-webpage",uiText("media.add_webpage"),Icons.Default.Language) { website=true }
    ))
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
        LazyVerticalGrid(GridCells.Adaptive(148.dp), modifier = Modifier.fillMaxSize().testTag("media-library"),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(shown, key = { it.id }) { entry ->
                val kindLabel = entry.kind.displayLabel()
                val title = mediaDisplayTitle(entry, phrases, kindLabel)
                Surface(onClick = feedbackClick { model.content.choose(entry, target) }, shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.testTag("media-select-" + entry.id)) {
                    Box(Modifier.fillMaxWidth().height(148.dp)) {
                        MediaCardBackdrop(entry, model.mediaRepository, Modifier.matchParentSize(), MaterialTheme.colorScheme.surfaceContainer)
                        Column(Modifier.fillMaxHeight().fillMaxWidth(.6f).padding(12.dp), verticalArrangement = Arrangement.Center) {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (entry.appleLive) uiText("media.apple_live") else kindLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FilledTonalIconButton(feedbackClick { menu = entry }, Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp).testTag("manage-media-" + entry.id)) {
                            Icon(Icons.Default.MoreVert, uiText("accessibility.manage_item", "name" to title), Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        EmptyContent(shown.isEmpty() && !importing, if (pagesOnly) Icons.Default.Language else Icons.Default.PermMedia,
            if (pagesOnly) uiText("media.no_webpages") else uiText("media.empty"),
            description = if (pagesOnly) uiText("media.empty_web") else uiText("media.empty_body"))
        }
    }
    if (active) menu?.let { entry -> MotionAlertDialog(onDismissRequest = { menu = null }, title = { Text(mediaDisplayTitle(entry, phrases, entry.kind.displayLabel())) }, text = { Column {
        TextButton(dialogFeedbackClick { menu = null; framing = entry }, Modifier.testTag("edit-thumbnail-framing")) { Text(uiText("media.thumbnail_framing")) }
        if (state.appleLivePhotos && entry.pluginId == null && entry.kind in setOf(MediaKind.IMAGE, MediaKind.MOTION_PHOTO)) TextButton(dialogFeedbackClick {
            menu = null; pairing = entry.id; pair.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        }, Modifier.testTag("pair-live-photo")) { Text(uiText("media.pair_apple_live")) }
        if (entry.kind == MediaKind.HTML && entry.pluginId == null) TextButton(dialogFeedbackClick { menu = null; scope.launch { runCatching { model.content.htmlFiles(target,entry.id) }.onFailure { error=it.message } } }) { Text(uiText("editor.manage_files")) }
        TextButton(dialogFeedbackClick { menu = null; deleting = entry }) { Text(uiText("action.delete"), color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { TextButton(dialogFeedbackClick { menu = null }) { Text(uiText("action.close")) } }) }
    if (active) deleting?.let { entry -> MotionAlertDialog(onDismissRequest = { deleting = null }, title = { Text(uiText("media.delete_prompt")) },
        text = { Text(uiText("media.delete_body")) }, confirmButton = { val closeDialog = rememberDialogClose(); TextButton(feedbackClick {
            scope.launch { try { model.content.removeAsync(entry.id); closeDialog { deleting = null } } catch (e: Exception) { error = e.message } }
        }) { Text(uiText("action.delete")) } }, dismissButton = { TextButton(dialogFeedbackClick { deleting = null }) { Text(uiText("action.cancel")) } }) }
    if (active && website) WebsiteDialog(model, close = { website = false })
    if (active) framing?.let { ThumbnailFramingDialog(it, model.mediaRepository) { framing = null } }
}

@Composable
private fun WebsiteDialog(model: AppViewModel, close: () -> Unit) {
    var url by remember { mutableStateOf("https://") }; var title by remember { mutableStateOf("") }; var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    MotionAlertDialog(onDismissRequest = close, title = { Text(uiText("media.add_web")) }, text = { Column {
        OutlinedTextField(url, { url = it }, label = { Text(uiText("media.https_address")) }); OutlinedTextField(title, { title = it }, label = { Text(uiText("media.optional_name")) })
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { val closeDialog = rememberDialogClose(); TextButton(feedbackClick { scope.launch { try { withContext(Dispatchers.IO) { model.mediaRepository.web(title, url) }; closeDialog(close) } catch (e: Exception) { error = e.message } } }) { Text(uiText("common.add")) } },
        dismissButton = { TextButton(dialogFeedbackClick(close)) { Text(uiText("action.cancel")) } })
}
