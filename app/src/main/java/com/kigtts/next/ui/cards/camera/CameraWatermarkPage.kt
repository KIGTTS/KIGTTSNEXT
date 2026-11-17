@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kigtts.next.ui.cards.camera

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kigtts.next.cards.*
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.ChoiceButtonGroup
import com.kigtts.next.ui.cards.rememberPhotoFrameGlass
import androidx.compose.foundation.selection.toggleable
import kotlin.math.roundToInt

@Composable
internal fun CameraWatermarkPage(options: CameraOptions, snapshot: Bitmap?, enabled: Boolean,
    store: (() -> Unit)?, edit: (CameraWatermarkAsset) -> Unit, back: () -> Unit) {
    val copy = rememberCameraCopy()
    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WatermarkHeader(copy.text("camera.frame_watermark"), back, store)
        val active = options.activeWatermark
        ChoiceButtonGroup(copy.text("camera.frame_watermark"), listOf(false, true), options.watermarkEnabled,
            { if (it) copy.text("option.on") else copy.text("action.close") }, { options.chooseWatermark(if (it) active?.id else null) },
            Modifier.padding(horizontal = 20.dp), enabled = enabled && active != null)
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(options.watermarks, key = { it.id }) { asset ->
                val selected = options.watermarkEnabled && active?.id == asset.id
                Column(Modifier.width(140.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = feedbackClick { options.chooseWatermark(asset.id) })
                    .testTag("camera-watermark/${asset.id}").padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    WatermarkPreview(options.watermarkSettings(asset), snapshot, 132.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(copy.label(asset), Modifier.weight(1f).padding(start = 6.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        IconButton(feedbackClick { edit(asset) }, Modifier.size(40.dp), enabled = enabled) { Icon(Icons.Default.Edit, copy.text("camera.status_edit", "value" to copy.label(asset))) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CameraWatermarkEditor(asset: CameraWatermarkAsset, options: CameraOptions, snapshot: Bitmap?, enabled: Boolean, back: () -> Unit) {
    val copy = rememberCameraCopy()
    val style = options.watermarkSettings(asset)
    fun change(value: CameraWatermarkStyle) = options.editWatermark(asset, value)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WatermarkHeader(copy.label(asset), back)
        WatermarkPreview(style, snapshot, 170.dp, Modifier.padding(horizontal = 20.dp))
        Column(Modifier.padding(horizontal = 20.dp)) {
            CameraWatermarkControls(style, enabled, ::change,
                drag = { options.editWatermark(asset, it, persist = false) }, finishDrag = { change(options.watermarkSettings(asset)) })
            TextButton(feedbackClick { change(asset.defaults) }, enabled = enabled) { Text(copy.text("camera.reset_default")) }
        }
    }
}

@Composable
internal fun CameraWatermarkControls(style: CameraWatermarkStyle, enabled: Boolean, change: (CameraWatermarkStyle) -> Unit,
    drag: (CameraWatermarkStyle) -> Unit = change, finishDrag: (() -> Unit)? = null) {
    val copy = rememberCameraCopy()
    WatermarkChoices(copy.text("camera.watermark_background"), WatermarkPalette.entries, style.palette, { copy.label(it) }, enabled) { change(style.copy(palette = it)) }
    WatermarkChoices(copy.text("camera.watermark_band_position"), WatermarkPosition.entries, style.position, { copy.label(it) }, enabled) { change(style.copy(position = it)) }
    Text(copy.text("camera.watermark_content_size"), style = MaterialTheme.typography.titleSmall)
    Slider(style.scale.toFloat(), { drag(style.copy(scale = it.roundToInt())) },
        enabled = enabled, valueRange = 75f..140f, onValueChangeFinished = finishDrag)
    WatermarkToggle(copy.text("camera.watermark_time"), style.showTime, enabled) { change(style.copy(showTime = it)) }
    WatermarkToggle(copy.text("camera.watermark_date"), style.showDate, enabled) { change(style.copy(showDate = it)) }
    WatermarkToggle(copy.text("camera.watermark_location"), style.showLocation, enabled) { change(style.copy(showLocation = it)) }
    WatermarkToggle(copy.text("camera.watermark_weather"), style.showWeather, enabled) { change(style.copy(showWeather = it)) }
    if (style.layout == WatermarkLayout.CLASSIC) {
        WatermarkToggle(copy.text("camera.watermark_exposure"), style.showExposure, enabled) { change(style.copy(showExposure = it)) }
    }
    if (style.layout == WatermarkLayout.CLASSIC || style.palette == WatermarkPalette.GLASS || style.edge > 0) {
        Text(copy.text("camera.watermark_width"), style = MaterialTheme.typography.titleSmall)
        Slider(style.edge.toFloat(), { drag(style.copy(edge = it.roundToInt())) },
            enabled = enabled, valueRange = 0f..12f, steps = 11, onValueChangeFinished = finishDrag)
    }
    if (style.layout == WatermarkLayout.CHECK_IN) WatermarkToggle(copy.text("camera.watermark_center"), style.center, enabled) { change(style.copy(center = it)) }
    WatermarkToggle(copy.text("camera.watermark_logo"), style.showLogo, enabled) { change(style.copy(showLogo = it)) }
}

@Composable private fun WatermarkHeader(title: String, back: () -> Unit, store: (() -> Unit)? = null) {
    val copy = rememberCameraCopy()
    Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
        IconButton(feedbackClick(back), Modifier.align(Alignment.CenterStart)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, copy.text("action.back"))
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (store != null) IconButton(feedbackClick(store), Modifier.align(Alignment.CenterEnd)) {
            Icon(Icons.Default.Storefront, copy.text("camera.watermark_store"))
        }
    }
}

@Composable private fun <T> WatermarkChoices(title: String, values: List<T>, selected: T, name: (T) -> String, enabled: Boolean, select: (T) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    ChoiceButtonGroup(title, values, selected, name, select, Modifier.padding(bottom = 10.dp), enabled = enabled, compactLabels = true)
}

@Composable private fun WatermarkToggle(label: String, checked: Boolean, enabled: Boolean, set: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = set)
        .padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable internal fun WatermarkPreview(style: CameraWatermarkStyle, snapshot: Bitmap?, height: Dp, modifier: Modifier = Modifier) {
    val copy = rememberCameraCopy()
    val context = LocalContext.current
    val preview = remember(snapshot) { snapshot?.asImageBitmap() }
    val geometry = remember(preview, style) { PhotoFrameSpec(preview?.width ?: 4, preview?.height ?: 3, style.encode()).geometry() }
    val glass = rememberPhotoFrameGlass(preview, style, geometry)
    BoxWithConstraints(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        val size = cameraPhotoWindow(maxWidth.value, maxHeight.value, geometry.aspectRatio.toFloat())
        val unit = size.width / geometry.paperWidth
        Box(Modifier.size(size.width.dp, size.height.dp).background(Color(style.palette.color))) {
            val photo = Modifier.offset((geometry.photoLeft * unit).dp, (geometry.photoTop * unit).dp)
                .size((geometry.photoWidth * unit).dp, (geometry.photoHeight * unit).dp)
            if (preview != null) Image(preview, null, photo, contentScale = ContentScale.Fit)
            else Box(photo.background(Brush.linearGradient(listOf(Color(0xFF8ABACA), Color(0xFF486B76), Color(0xFFE0AB74)))))
            Canvas(Modifier.matchParentSize()) {
                drawIntoCanvas { drawCameraWatermark(context, it.nativeCanvas, style, geometry,
                    WatermarkFacts("2026.11.13", "09:41", "24mm  ·  ƒ/1.6  ·  1/250s  ·  ISO 100", copy.text("camera.sample_place"), copy.text("camera.sample_weather")), this.size.width, this.size.height, glass) }
            }
        }
    }
}
