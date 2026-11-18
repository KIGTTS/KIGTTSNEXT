@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kigtts.next.ui.settings

import com.kigtts.next.i18n.uiText

import android.graphics.BitmapFactory
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kigtts.next.theme.*
import com.kigtts.next.ui.components.feedbackClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun BackgroundCropScreen(source: String, onDismiss: () -> Unit, onSave: (String, Long) -> Unit) {
    val context = LocalContext.current
    var crop by rememberSaveable(source, stateSaver = listSaver<BackgroundCrop, Float>(
        save = { listOf(it.x, it.y, it.zoom) }, restore = { BackgroundCrop(it[0], it[1], it[2]) })) { mutableStateOf(BackgroundCrop()) }
    var saving by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val bitmap by produceState<android.graphics.Bitmap?>(null, source) {
        value = withContext(Dispatchers.IO) { AppearanceAssets(context).file(source)?.let { BitmapFactory.decodeFile(it.path) } }
        if (value == null) error = "无法读取这张图片，请重新选择"
    }
    LaunchedEffect(confirm) {
        if (!confirm || bitmap == null) return@LaunchedEffect
        saving = true
        try {
            val (key, seed) = withContext(Dispatchers.IO) {
                val output = cropBackground(requireNotNull(bitmap), crop)
                try {
                    val key = AppearanceAssets(context).saveBackground(output)
                    AppearanceBackgrounds.load(context, key)
                    AppearanceBackgrounds.load(context, key, true)
                    AppearanceBackgrounds.load(context, key, BackgroundBlur.PAGE)
                    key to backgroundThemeSeed(output)
                } finally { output.recycle() }
            }
            onSave(key, seed)
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (e: Exception) { android.util.Log.w("BackgroundCrop", "Could not save background", e); error = "背景未能保存，请重试" }
        finally { saving = false; confirm = false }
    }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = androidx.compose.ui.platform.LocalView.current
        SideEffect {
            (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let { window ->
                androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = false; isAppearanceLightNavigationBars = false
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val extent = with(density) { (minOf(maxWidth, maxHeight) - 24.dp).toPx() }
            val image = bitmap
            Canvas(Modifier.fillMaxSize().testTag("background-crop-square").pointerInput(image, saving, extent) {
                if (image != null && !saving) detectTransformGestures { center, pan, zoom, _ ->
                    val left = (size.width - extent) / 2; val top = (size.height - extent) / 2
                    crop = crop.move(image.width, image.height, extent, pan.x, pan.y, zoom, center.x - left, center.y - top)
                }
            }) {
                if (image != null) {
                    val rect = crop.bounds(image.width, image.height)
                    val left = (size.width - extent) / 2; val top = (size.height - extent) / 2
                    val scale = extent / rect.width()
                    withTransform({ translate(left - rect.left * scale, top - rect.top * scale); scale(scale, scale, Offset.Zero) }) {
                        drawImage(image.asImageBitmap())
                    }
                    val shade = Color.Black.copy(alpha = .62f)
                    drawRect(shade, Offset.Zero, Size(size.width, top))
                    drawRect(shade, Offset(0f, top + extent), Size(size.width, top))
                    drawRect(shade, Offset(0f, top), Size(left, extent))
                    drawRect(shade, Offset(left + extent, top), Size(left, extent))
                    val gap = extent * (1f - 9f / 16f) / 2
                    listOf(Offset(left, top), Offset(left + extent - gap, top), Offset(left, top + extent - gap),
                        Offset(left + extent - gap, top + extent - gap)).forEach {
                        drawRect(Color.Black.copy(alpha = .28f), it, Size(gap, gap))
                    }
                }
            }
            TopAppBar(title = { Text(uiText("theme.background_crop")) }, navigationIcon = {
                IconButton(feedbackClick { if (!saving) onDismiss() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "取消裁剪") }
            }, actions = {
                if (saving) CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), color = Color.White)
                else IconButton(feedbackClick { if (bitmap != null) confirm = true }, Modifier.testTag("background-crop-save")) {
                    Icon(Icons.Default.Check, "保存背景")
                }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent,
                titleContentColor = Color.White, navigationIconContentColor = Color.White, actionIconContentColor = Color.White))
            if (image == null && error == null) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            Text(error ?: "双指调整位置与缩放", Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(24.dp),
                color = Color.White.copy(alpha = .9f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
