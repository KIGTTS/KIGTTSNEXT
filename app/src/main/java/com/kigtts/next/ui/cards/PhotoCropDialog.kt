@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kigtts.next.ui.cards

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kigtts.next.cards.PhotoCrop
import com.kigtts.next.ui.components.feedbackClick
import kotlin.math.abs

@Composable
internal fun PhotoCropDialog(image: ImageBitmap?, initial: PhotoCrop, close: () -> Unit,
    apply: (PhotoCrop, Int, Int) -> Unit) {
    var crop by remember { mutableStateOf(initial) }
    Dialog(close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(containerColor = Color.Black, modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(title = { Text(uiText("photo.crop")) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black, titleContentColor = Color.White,
                        actionIconContentColor = Color.White, navigationIconContentColor = Color.White),
                    navigationIcon = { IconButton(feedbackClick(close)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "取消裁剪") } },
                    actions = {
                        TextButton(feedbackClick { crop = PhotoCrop.Full }, enabled = crop != PhotoCrop.Full) { Text(uiText("common.reset"), color = Color.White.copy(alpha = if (crop == PhotoCrop.Full) .4f else 1f)) }
                        Button(feedbackClick { image?.let { apply(crop, it.width, it.height) } },
                            Modifier.padding(end = 12.dp), enabled = image != null) { Text(uiText("overlay.category.apps")) }
                    })
            }) { insets ->
            Box(Modifier.fillMaxSize().padding(insets).background(Color.Black).padding(18.dp), contentAlignment = Alignment.Center) {
                if (image == null) CircularProgressIndicator()
                else BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val ratio = image.width.toFloat() / image.height
                    val width = minOf(maxWidth, maxHeight * ratio)
                    val height = width / ratio
                    Box(Modifier.size(width, height)) {
                        Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                        PhotoCropHandles(crop, { crop = it }, ratio, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoCropHandles(crop: PhotoCrop, update: (PhotoCrop) -> Unit, ratio: Float, modifier: Modifier) {
    val current = rememberUpdatedState(crop)
    val change = rememberUpdatedState(update)
    Canvas(modifier.testTag("photo-crop-frame").pointerInput(ratio) {
        var handle: CropHandle? = null
        detectDragGestures(onDragStart = { start ->
            val rect = current.value.rect(size.width.toFloat(), size.height.toFloat())
            handle = CropHandle.hit(rect, start, 28.dp.toPx())
        }) { pointer, delta ->
            val target = handle ?: return@detectDragGestures
            pointer.consume()
            val next = target.move(current.value, delta.x / size.width, delta.y / size.height)
            if (next.valid && ratio * next.width / next.height in .05f..20f) change.value(next)
        }
    }) {
        val r = crop.rect(size.width, size.height)
        val shade = Color.Black.copy(alpha = .62f)
        fun mask(l: Float, t: Float, right: Float, bottom: Float) {
            if (right > l && bottom > t) drawRect(shade, Offset(l, t), Size(right - l, bottom - t))
        }
        mask(0f, 0f, size.width, r.top); mask(0f, r.bottom, size.width, size.height)
        mask(0f, r.top, r.left, r.bottom); mask(r.right, r.top, size.width, r.bottom)
        drawRect(Color.White, r.topLeft, r.size, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        for (index in 1..2) {
            val x = r.left + r.width * index / 3; val y = r.top + r.height * index / 3
            drawLine(Color.White.copy(alpha = .45f), Offset(x, r.top), Offset(x, r.bottom), 1.dp.toPx())
            drawLine(Color.White.copy(alpha = .45f), Offset(r.left, y), Offset(r.right, y), 1.dp.toPx())
        }
        val length = minOf(20.dp.toPx(), r.width / 3, r.height / 3)
        listOf(r.topLeft, r.topRight, r.bottomLeft, r.bottomRight).forEachIndexed { index, corner ->
            drawLine(Color.White, corner, corner + Offset(if (index % 2 == 0) length else -length, 0f), 3.dp.toPx())
            drawLine(Color.White, corner, corner + Offset(0f, if (index < 2) length else -length), 3.dp.toPx())
        }
    }
}

private fun PhotoCrop.rect(width: Float, height: Float) = Rect(left * width, top * height, right * width, bottom * height)

private data class CropHandle(val x: Int, val y: Int) {
    fun move(crop: PhotoCrop, dx: Float, dy: Float): PhotoCrop {
        if (x == 0 && y == 0) {
            val tx = dx.coerceIn(-crop.left, 1f - crop.right)
            val ty = dy.coerceIn(-crop.top, 1f - crop.bottom)
            return PhotoCrop(crop.left + tx, crop.top + ty, crop.right + tx, crop.bottom + ty)
        }
        return PhotoCrop(if (x < 0) (crop.left + dx).coerceIn(0f, crop.right - .035f) else crop.left,
            if (y < 0) (crop.top + dy).coerceIn(0f, crop.bottom - .035f) else crop.top,
            if (x > 0) (crop.right + dx).coerceIn(crop.left + .035f, 1f) else crop.right,
            if (y > 0) (crop.bottom + dy).coerceIn(crop.top + .035f, 1f) else crop.bottom)
    }

    companion object {
        fun hit(rect: Rect, point: Offset, slop: Float): CropHandle? {
            if (!rect.inflate(slop).contains(point)) return null
            fun edge(value: Float, start: Float, end: Float): Int {
                val before = abs(value - start); val after = abs(value - end)
                return if (minOf(before, after) > slop) 0 else if (before <= after) -1 else 1
            }
            return CropHandle(edge(point.x, rect.left, rect.right), edge(point.y, rect.top, rect.bottom))
        }
    }
}
