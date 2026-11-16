@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.kigtts.next.ui.album

import com.kigtts.next.i18n.uiText

import com.kigtts.next.i18n.uiCopy

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.media.*
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.LocalCornerRadius
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay

/** A MediaStore gallery, shared by the camera, the application and Android's image picker intents. */
@Composable internal fun DeviceAlbumScreen(onBack: () -> Unit, onEdit: (Uri) -> Unit,
    onCamera: (() -> Unit)? = null, message: (String) -> Unit = {},
    choose: ((List<AlbumMedia>) -> Unit)? = null, multiple: Boolean = false,
    initialUri: Uri? = null, imagesOnly: Boolean = false, videosOnly: Boolean = false,
    requestedUri: Uri? = null, requestConsumed: () -> Unit = {}) {
    val context = LocalContext.current
    val repository = remember(context) { DeviceAlbum(context.applicationContext) }
    var albumId by rememberSaveable { mutableStateOf("all") }
    var choosingAlbum by rememberSaveable { mutableStateOf(false) }
    var showingCollectionGrid by rememberSaveable { mutableStateOf(false) }
    var zoom by rememberSaveable { mutableIntStateOf(0) }
    var all by remember { mutableStateOf(emptyList<AlbumMedia>()) }
    var loading by remember { mutableStateOf(true) }
    var revision by remember { mutableIntStateOf(0) }
    var readable by remember { mutableStateOf(DeviceAlbum.canRead(context, imagesOnly)) }
    var failure by remember { mutableStateOf<String?>(null) }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var preview by remember { mutableStateOf<Uri?>(initialUri) }
    LaunchedEffect(requestedUri) { if (requestedUri != null) { preview = requestedUri; requestConsumed() } }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        readable = DeviceAlbum.canRead(context, imagesOnly); revision++
    }
    LaunchedEffect(repository, revision, imagesOnly, videosOnly) {
        repository.changes.collectLatest {
            delay(120)
            try { all = repository.load(imagesOnly, videosOnly); failure = null }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: SecurityException) { failure = context.uiCopy("album.permission") }
            catch (_: Exception) { failure = context.uiCopy("album.failed") }
            finally { loading = false }
        }
    }
    val allLabel = if (imagesOnly) "全部照片" else if (videosOnly) "全部视频" else "全部"
    val albums = remember(all, allLabel) { albumCollections(all, allLabel) }
    val album = albums.firstOrNull { it.id == albumId } ?: albums.first()
    val photos = album.media
    val grid = key(album.id) { rememberLazyGridState() }
    val checked = remember(selected) { selected.toSet() }
    fun toggle(media: AlbumMedia) {
        val id = media.uri.toString()
        selected = if (id in checked) selected - id else selected + id
    }
    fun selectAlbum(choice: AlbumCollection, fromGrid: Boolean) {
        if (albumId != choice.id || fromGrid) zoom = 0
        albumId = choice.id
        choosingAlbum = false
        if (fromGrid) showingCollectionGrid = false
    }
    fun back() {
        when {
            choosingAlbum -> choosingAlbum = false
            showingCollectionGrid -> showingCollectionGrid = false
            selecting -> { selecting = false; selected = emptyList() }
            else -> onBack()
        }
    }
    BackHandler(choosingAlbum || showingCollectionGrid || selecting) { back() }
    Scaffold(topBar = {
        TopAppBar(title = {
            if (showingCollectionGrid) Text(uiText("nav.album"))
            else AlbumTitle(album, choosingAlbum) { choosingAlbum = !choosingAlbum }
        }, navigationIcon = { IconButton(feedbackClick(::back)) { Icon(Icons.Default.ArrowBack, uiText("action.back")) } },
            actions = {
                if (!choosingAlbum && !showingCollectionGrid) {
                    if (choose == null && !selecting) IconButton(feedbackClick { openSystemAlbum(context, message) }) {
                        Icon(Icons.Default.OpenInNew, uiText("album.system"))
                    }
                    if (onCamera != null && !selecting) IconButton(feedbackClick(onCamera)) { Icon(Icons.Default.CameraAlt, uiText("album.camera")) }
                    if (choose == null) IconButton(feedbackClick { selecting = !selecting; selected = emptyList() }) {
                        Icon(if (selecting) Icons.Default.Close else Icons.Default.Checklist, if (selecting) "取消选择" else "选择照片")
                    } else if (multiple) TextButton(feedbackClick { choose(all.filter { it.uri.toString() in checked }) },
                        enabled = selected.isNotEmpty()) { Text(if (selected.isEmpty()) uiText("action.done") else
                            uiText("action.done_count", "count" to selected.size.toString())) }
                }
            })
    }, bottomBar = {
        if (!choosingAlbum && !showingCollectionGrid && selecting) Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly) {
            val allChecked = photos.isNotEmpty() && photos.all { it.uri.toString() in checked }
            TextButton(feedbackClick {
                val ids = photos.map { it.uri.toString() }.toSet()
                selected = (if (allChecked) checked - ids else checked + ids).toList()
            }, enabled = photos.isNotEmpty()) { Text(if (allChecked) uiText("album.deselect_all") else uiText("album.select_all")) }
            Button(feedbackClick { shareAlbumMedia(context, all.filter { it.uri.toString() in checked }) },
                enabled = selected.isNotEmpty()) {
                Icon(Icons.Default.Share, null)
                Text(uiText("action.share_count", "count" to selected.size.toString()))
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).testTag("device-album")) {
            if (!readable) TextButton(feedbackClick { request.launch(DeviceAlbum.permissions(imagesOnly)) },
                Modifier.fillMaxWidth()) { Text(uiText("album.view_device")) }
            if (failure != null) TextButton(feedbackClick { revision++ }, Modifier.fillMaxWidth()) { Text(failure.orEmpty()) }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val availableHeight = maxHeight
                AnimatedContent(showingCollectionGrid, Modifier.fillMaxSize(), transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "album-zoom-mode") { collections ->
                    if (collections) {
                        AlbumCollectionGrid(albums, album.id, Modifier.fillMaxSize(),
                            onPinchOpen = { selectAlbum(it, fromGrid = true) }) { selectAlbum(it, fromGrid = true) }
                    } else AlbumPhotoGrid(photos, grid, loading, zoom, { zoom = it }, { showingCollectionGrid = true },
                    selecting || choose != null && multiple, checked,
                    tapPhoto = { media ->
                        if (selecting || choose != null && multiple) toggle(media)
                        else if (choose != null) choose(listOf(media)) else preview = media.uri
                    }, holdPhoto = if (choose != null && !multiple) null else ({ media ->
                        selecting = choose == null
                        toggle(media)
                    }), modifier = Modifier.fillMaxSize())
                }
                androidx.compose.animation.AnimatedVisibility(choosingAlbum, Modifier.matchParentSize(), enter = fadeIn(), exit = fadeOut()) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = .23f))
                        .clickable(remember { MutableInteractionSource() }, indication = null,
                            onClickLabel = "收起相册", onClick = feedbackClick { choosingAlbum = false }))
                }
                androidx.compose.animation.AnimatedVisibility(choosingAlbum, Modifier.align(Alignment.TopCenter),
                    enter = fadeIn() + slideInVertically { -it / 8 }, exit = fadeOut() + slideOutVertically { -it / 8 }) {
                    Surface(Modifier.widthIn(max = 840.dp).fillMaxWidth()
                        .height((availableHeight * .80f).coerceAtMost(570.dp)),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(
                            bottomStart = LocalCornerRadius.current, bottomEnd = LocalCornerRadius.current),
                        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .87f)) {
                        AlbumDropdownList(albums, album.id, Modifier.fillMaxSize()) { choice ->
                            selectAlbum(choice, fromGrid = false)
                        }
                    }
                }
            }
        }
    }
    preview?.let { uri ->
        val available = if (photos.any { it.uri == uri }) photos else all
        AlbumPreview(available, uri, { preview = null }, onEdit, message, removed = { revision++; preview = null })
    }
}

internal fun shareAlbumMedia(context: android.content.Context, photos: List<AlbumMedia>) {
    if (photos.isEmpty()) return
    val multiple = photos.size > 1
    val intent = Intent(if (multiple) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
        type = if (photos.all { !it.video }) "image/*" else if (photos.all { it.video }) "video/*" else "*/*"
        if (multiple) putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(photos.map { it.uri })) else putExtra(Intent.EXTRA_STREAM, photos.first().uri)
        clipData = android.content.ClipData.newUri(context.contentResolver, "照片", photos.first().uri).apply { photos.drop(1).forEach { addItem(android.content.ClipData.Item(it.uri)) } }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享"))
}

private fun openSystemAlbum(context: android.content.Context, message: (String) -> Unit) {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY)
    val targets = context.packageManager.queryIntentActivities(intent, 0).filter { it.activityInfo.packageName != context.packageName }
    val target = targets.firstOrNull() ?: run { message(context.uiCopy("album.system_missing")); return }
    runCatching { context.startActivity(intent.setClassName(target.activityInfo.packageName, target.activityInfo.name)) }
        .onFailure { message(context.uiCopy("album.system_failed")) }
}
