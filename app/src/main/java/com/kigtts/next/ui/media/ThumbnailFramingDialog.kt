package com.kigtts.next.ui.media

import com.kigtts.next.i18n.uiText

import com.kigtts.next.ui.components.MotionTextButton

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.next.ui.components.dialogFeedbackClick
import com.kigtts.next.ui.components.rememberDialogClose
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import com.kigtts.next.media.*
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.settings.SettingChoiceGroup
import kotlinx.coroutines.*
import kotlin.math.min

@Composable
internal fun ThumbnailFramingDialog(entry: MediaEntry, repository: MediaRepository, close: () -> Unit) {
    var mode by remember(entry.id) { mutableStateOf(entry.thumbnailMode) }
    var x by remember(entry.id) { mutableDoubleStateOf(entry.focusX) }
    var y by remember(entry.id) { mutableDoubleStateOf(entry.focusY) }
    val revision by repository.thumbnails.revision.collectAsState()
    val source by produceState<ImageBitmap?>(null, entry.id, revision) { value = repository.thumbnails.source(entry)?.asImageBitmap() }
    var saving by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val preview = entry.copy(thumbnailMode = mode, focusX = x, focusY = y)
    MotionAlertDialog(onDismissRequest = close, dismissEnabled = !saving, title = { Text(uiText("media.thumbnail_framing")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingChoiceGroup("缩略图取景", ThumbnailMode.entries, mode, { it.label }, { mode = it })
            Text(when (mode) { ThumbnailMode.FOCUS -> uiText("media.thumbnail_focus"); ThumbnailMode.FIT -> uiText("media.thumbnail_fit"); ThumbnailMode.AUTO -> uiText("media.thumbnail_auto") }, style = MaterialTheme.typography.bodySmall)
            val image = source
            if (image != null) {
                var size by remember { mutableStateOf(IntSize.Zero) }
                val marker = MaterialTheme.colorScheme.primary
                Canvas(Modifier.fillMaxWidth().height(168.dp).testTag("thumbnail-focus-source").onSizeChanged { size = it }
                    .pointerInput(image, mode, size) {
                        if (mode == ThumbnailMode.FOCUS) detectTapGestures { point ->
                            val scale = min(size.width.toFloat() / image.width, size.height.toFloat() / image.height)
                            val left = (size.width - image.width * scale) / 2; val top = (size.height - image.height * scale) / 2
                            if (scale > 0 && point.x in left..left + image.width * scale && point.y in top..top + image.height * scale) {
                                x = ((point.x - left) / (image.width * scale)).toDouble().coerceIn(0.0, 1.0)
                                y = ((point.y - top) / (image.height * scale)).toDouble().coerceIn(0.0, 1.0)
                            }
                        }
                    }) {
                    val scale = min(this.size.width / image.width, this.size.height / image.height)
                    val w = (image.width * scale).toInt(); val h = (image.height * scale).toInt()
                    val offset = IntOffset(((this.size.width - w) / 2).toInt(), ((this.size.height - h) / 2).toInt())
                    drawImage(image, dstOffset = offset, dstSize = IntSize(w, h))
                    if (mode == ThumbnailMode.FOCUS) {
                        val point = Offset(offset.x + (x * w).toFloat(), offset.y + (y * h).toFloat())
                        drawCircle(Color.White, 12.dp.toPx(), point, style = androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx()))
                        drawCircle(marker, 12.dp.toPx(), point, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                    }
                }
            } else Text(if (entry.kind in setOf(MediaKind.HTML, MediaKind.WEB)) uiText("media.web_thumbnail_unavailable") else uiText("media.image_cover_unavailable"), style = MaterialTheme.typography.bodySmall)
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Box(Modifier.fillMaxWidth().height(88.dp)) {
                    MediaCardBackdrop(preview, repository, Modifier.matchParentSize())
                    Text(if(entry.kind in setOf(MediaKind.VIDEO,MediaKind.AUDIO))uiText("media.subtitle_preview")else entry.name,
                        Modifier.fillMaxWidth(.6f).padding(12.dp), maxLines = 2, style = MaterialTheme.typography.bodyLarge)
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { val closeDialog = rememberDialogClose(); MotionTextButton(enabled = !saving, onClick = feedbackClick { scope.launch {
        saving = true
        try { withContext(Dispatchers.IO) { repository.thumbnail(entry.id, mode, x, y) }; closeDialog(close) }
        catch (cancel: CancellationException) { throw cancel }
        catch (e: Exception) { error = e.message ?: "保存失败"; saving = false }
    } }, modifier = Modifier.testTag("save-thumbnail-framing")) { Text(uiText("action.save")) } },
        dismissButton = { MotionTextButton(dialogFeedbackClick(close), enabled = !saving) { Text(uiText("action.cancel")) } })
}
