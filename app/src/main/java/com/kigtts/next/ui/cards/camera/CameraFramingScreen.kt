@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards.camera

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.media.MediaBitmapDecoder
import com.kigtts.next.state.CardFrameRequest
import com.kigtts.next.ui.cards.CardPhotoBackground
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ln

internal val captureFrameFormats = listOf(PolaroidFormat.MINI, PolaroidFormat.SQUARE, PolaroidFormat.WIDE)
internal fun closestCaptureFormat(width: Int, height: Int): PolaroidFormat = captureFrameFormats.minBy {
    abs(ln((width.toDouble() / height.coerceAtLeast(1)) / it.geometry.photoAspectRatio))
}

/** A viewport transform only. The camera's original JPEG never passes through a crop encoder. */
@Composable internal fun CameraFramingScreen(request: CardFrameRequest, busy: Boolean, cancel: () -> Unit,
    confirm: (PolaroidFormat, Double, Double, Double) -> Unit) {
    val image by produceState<Bitmap?>(null, request.original) {
        value = withContext(Dispatchers.IO) { MediaBitmapDecoder.decode(request.display, 2048) }
    }
    var format by rememberSaveable(request.requestId) { mutableStateOf<PolaroidFormat?>(null) }
    var x by rememberSaveable(request.requestId) { mutableDoubleStateOf(.5) }
    var y by rememberSaveable(request.requestId) { mutableDoubleStateOf(.5) }
    var zoom by rememberSaveable(request.requestId) { mutableDoubleStateOf(1.0) }
    fun choose(selected: PolaroidFormat) {
        format = selected; x = .5; y = .5
        image?.let { val source = it.width.toDouble() / it.height; val target = selected.geometry.photoAspectRatio
            zoom = maxOf(source / target, target / source).coerceIn(1.0, 8.0) }
    }
    LaunchedEffect(image) { image?.let { if (format == null) choose(closestCaptureFormat(it.width, it.height)) } }
    val current = format ?: PolaroidFormat.SQUARE
    val card = PolaroidCard(id = "system-camera-framing", title = "", photoX = x, photoY = y, photoZoom = zoom)
    Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("camera-framing")) {
        TopAppBar(title = { Text("照片构图") }, navigationIcon = {
            IconButton(onClick = feedbackClick(cancel), enabled = !busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "取消") }
        }, actions = {
            IconActionHint("使用照片") {
                IconButton(onClick = feedbackClick { confirm(current, x, y, zoom) },
                    Modifier.testTag("capture-frame-confirm").iconLongAction(!busy && image != null, null), enabled = !busy && image != null) {
                    Icon(Icons.Default.Check, "使用照片")
                }
            }
        })
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            val choices: @Composable () -> Unit = {
                captureFrameFormats.forEach { option ->
                    CaptureFormatTab(option, current == option, !busy) { choose(option) }
                }
            }
            val photo: @Composable (Modifier) -> Unit = { modifier ->
                BoxWithConstraints(modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                    val size = cameraPhotoWindow(maxWidth.value, maxHeight.value, current.geometry.photoAspectRatio.toFloat())
                    Box(Modifier.size(size.width.dp, size.height.dp).clipToBounds()) {
                        CardPhotoBackground(card, image?.asImageBitmap(), !busy, { x = it.photoX; y = it.photoY; zoom = it.photoZoom }, {}, Modifier.fillMaxSize())
                    }
                }
            }
            if (landscape) Row(Modifier.fillMaxSize()) {
                photo(Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.width(144.dp).fillMaxHeight().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) { choices() }
                    Text("双指调整位置与缩放", style = MaterialTheme.typography.bodySmall)
                }
            } else Column(Modifier.fillMaxSize()) {
                photo(Modifier.weight(1f).fillMaxWidth())
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) { choices() }
                Text("双指调整位置与缩放", Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
