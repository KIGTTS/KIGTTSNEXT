@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.kigtts.next.ui.album

import com.kigtts.next.i18n.uiText

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kigtts.next.media.AlbumMedia
import com.kigtts.next.ui.components.feedbackClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable internal fun AlbumPreview(photos: List<AlbumMedia>, initial: Uri, dismiss: () -> Unit,
    edit: (Uri) -> Unit, message: (String) -> Unit, removed: () -> Unit) {
    val context = LocalContext.current
    val dateLabel = rememberAlbumDateFormat()
    val media = remember(photos, initial) { if (photos.any { it.uri == initial }) photos else
        listOf(AlbumMedia(initial, "照片", context.contentResolver.getType(initial) ?: "image/jpeg", System.currentTimeMillis(), 0, 0)) + photos }
    val pager = rememberPagerState(initialPage = media.indexOfFirst { it.uri == initial }.coerceAtLeast(0)) { media.size }
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var retryDelete by remember { mutableStateOf<Uri?>(null) }
    val approveDelete = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val retry = retryDelete; retryDelete = null
            if (retry != null) scope.launch {
                try { withContext(Dispatchers.IO) { context.contentResolver.delete(retry, null, null) }; removed() }
                catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                catch (_: Exception) { message("未能删除照片") }
            }
            else removed()
        }
        deleting = false
    }
    fun delete(photo: AlbumMedia) {
        if (deleting) return
        deleting = true
        scope.launch {
            try {
                require(photo.uri.authority == "media") { "这张图片请在来源应用中删除" }
                if (Build.VERSION.SDK_INT >= 30) {
                    val pending = MediaStore.createDeleteRequest(context.contentResolver, listOf(photo.uri))
                    approveDelete.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                } else {
                    withContext(Dispatchers.IO) { context.contentResolver.delete(photo.uri, null, null) }; deleting = false; removed()
                }
            } catch (error: Exception) {
                if (Build.VERSION.SDK_INT == 29 && error is RecoverableSecurityException) {
                    retryDelete = photo.uri
                    approveDelete.launch(IntentSenderRequest.Builder(error.userAction.actionIntent.intentSender).build())
                } else { deleting = false; message(error.message ?: "未能删除照片") }
            }
        }
    }
    Dialog(dismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val photo = media[pager.currentPage]
        Surface(color = Color.Black) { Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(feedbackClick(dismiss)) { Icon(Icons.Default.ArrowBack, "返回", tint = Color.White) }
                Text("${pager.currentPage + 1} / ${media.size}", color = Color.White, modifier = Modifier.weight(1f))
                Text(dateLabel(photo.day), Modifier.padding(end = 16.dp), color = Color.LightGray)
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), pageSpacing = 12.dp) { index ->
                val item = media[index]
                if (item.video && pager.currentPage == index) AlbumVideo(item)
                else AlbumZoomImage(item)
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(feedbackClick { shareAlbumMedia(context, listOf(photo)) }) { Icon(Icons.Default.Share, "分享", tint = Color.White) }
                if (!photo.video) IconButton(feedbackClick { dismiss(); edit(photo.uri) }) { Icon(Icons.Default.Edit, "编辑图片", tint = Color.White) }
                IconButton(feedbackClick { confirm = true }, enabled = !deleting) { Icon(Icons.Default.DeleteOutline, "删除", tint = Color.White) }
            }
        } }
        if (confirm) AlertDialog({ confirm = false }, title = { Text(uiText(if (photo.video) "album.delete_video" else "album.delete_photo")) },
            text = { Text(uiText("album.remove_from_device")) },
            confirmButton = { TextButton(feedbackClick { confirm = false; delete(photo) }) { Text(uiText("action.delete")) } },
            dismissButton = { TextButton(feedbackClick { confirm = false }) { Text(uiText("action.cancel")) } })
    }
}

@Composable private fun AlbumZoomImage(media: AlbumMedia) {
    var zoom by remember(media.uri) { mutableFloatStateOf(1f) }
    var pan by remember(media.uri) { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().clipToBounds().pointerInput(media.uri) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                if (event.changes.count { it.pressed } > 1 || zoom > 1f) {
                    zoom = (zoom * event.calculateZoom()).coerceIn(1f, 6f)
                    pan = if (zoom <= 1f) Offset.Zero else (pan + event.calculatePan()).let {
                        Offset(it.x.coerceIn(-size.width * (zoom - 1) / 2, size.width * (zoom - 1) / 2),
                            it.y.coerceIn(-size.height * (zoom - 1) / 2, size.height * (zoom - 1) / 2))
                    }
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }) {
        AlbumImage(media, Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y }, full = true)
    }
}

@Composable private fun AlbumVideo(media: AlbumMedia) {
    val context = LocalContext.current
    val view = remember(media.uri) { VideoView(context).also { player ->
        player.setVideoURI(media.uri)
        player.setMediaController(MediaController(context).apply { setAnchorView(player) })
        player.setOnPreparedListener { player.start() }
    } }
    DisposableEffect(view) { onDispose { view.stopPlayback() } }
    AndroidView({ view }, Modifier.fillMaxSize())
}
