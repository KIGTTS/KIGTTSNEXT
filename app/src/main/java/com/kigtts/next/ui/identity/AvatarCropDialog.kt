@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kigtts.next.ui.identity

import com.kigtts.next.i18n.uiText

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kigtts.next.cards.StickerRepository
import com.kigtts.next.identity.saveAvatarCrop
import com.kigtts.next.media.MediaBitmapDecoder
import com.kigtts.next.theme.BackgroundCrop
import com.kigtts.next.ui.components.feedbackClick
import kotlinx.coroutines.*
import java.io.File

@Composable internal fun AvatarCropDialog(source: File, stickers: StickerRepository, close: () -> Unit, save: (String) -> Unit) {
    SquareImageCropDialog(source, close, save, roundPreview = true, title = uiText("profile.avatar_crop")) { bitmap, crop ->
        saveAvatarCrop(stickers, bitmap, crop)
    }
}

@Composable internal fun <T> SquareImageCropDialog(source: File, close: () -> Unit, save: (T) -> Unit,
    roundPreview: Boolean, title: String, write: (Bitmap, BackgroundCrop) -> T) {
    var crop by rememberSaveable(source.path, stateSaver = listSaver<BackgroundCrop, Float>(
        save = { listOf(it.x, it.y, it.zoom) }, restore = { BackgroundCrop(it[0], it[1], it[2]) })) { mutableStateOf(BackgroundCrop()) }
    var saving by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val bitmap by produceState<Bitmap?>(null, source.path) {
        value = withContext(Dispatchers.IO) { runCatching { MediaBitmapDecoder.decode(source, 2048) }.getOrNull() }
        if (value == null) error = "无法读取这张图片，请重新选择"
    }
    Dialog({ if (!saving) close() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = androidx.compose.ui.platform.LocalView.current
        SideEffect {
            (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let { window ->
                androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = false; isAppearanceLightNavigationBars = false
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
            val extent = with(LocalDensity.current) { (minOf(maxWidth, maxHeight) - 32.dp).coerceAtLeast(1.dp).toPx() }
            val image = bitmap
            Canvas(Modifier.fillMaxSize().testTag("avatar-crop-canvas").pointerInput(image, saving, extent) {
                if (image != null && !saving) detectTransformGestures { center, pan, zoom, _ ->
                    crop = crop.move(image.width, image.height, extent, pan.x, pan.y, zoom,
                        center.x - (size.width - extent) / 2, center.y - (size.height - extent) / 2)
                }
            }) {
                if (image != null) {
                    val rect = crop.bounds(image.width, image.height)
                    val left = (size.width - extent) / 2; val top = (size.height - extent) / 2; val scale = extent / rect.width()
                    withTransform({ translate(left - rect.left * scale, top - rect.top * scale); scale(scale, scale, Offset.Zero) }) {
                        drawImage(image.asImageBitmap())
                    }
                    val outside = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size))
                        val preview = androidx.compose.ui.geometry.Rect(Offset(left, top), Size(extent, extent))
                        if (roundPreview) addOval(preview) else addRect(preview)
                    }
                    drawPath(outside, Color.Black.copy(alpha = .65f))
                }
            }
            TopAppBar(title = { Text(title) }, navigationIcon = {
                IconButton(feedbackClick { if (!saving) close() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, uiText("accessibility.cancel_crop")) }
            }, actions = {
                if (saving) CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), color = Color.White)
                else IconButton(feedbackClick {
                    if (image != null) scope.launch {
                        saving = true
                        try { save(withContext(Dispatchers.IO) { write(image, crop) }) }
                        catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) { error = "头像未能保存，请重试" }
                        finally { saving = false }
                    }
                }, enabled = image != null, modifier = Modifier.testTag("avatar-crop-save")) { Icon(Icons.Default.Check, uiText("accessibility.use_avatar")) }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = Color.White,
                navigationIconContentColor = Color.White, actionIconContentColor = Color.White))
            if (image == null && error == null) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            Text(error ?: uiText("profile.pinch_drag"), Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(24.dp),
                color = Color.White, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
