@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kigtts.next.ui.cards.camera

import android.graphics.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.ToolButton
import com.kigtts.next.media.color.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun CameraFilters(filter: PhotoFilter, enabled: Boolean, select: (PhotoFilter) -> Unit,
    vertical: Boolean = false, strength: Float = 1f, changeStrength: (Float) -> Unit = {},
    custom: List<CameraLutChoice> = emptyList(), selectedLut: String? = null, selectLut: (String) -> Unit = {}, importLut: () -> Unit = {},
    effects: PhotoEffects = PhotoEffects.NONE, changeEffects: (PhotoEffects) -> Unit = {}, collapse: (() -> Unit)? = null) {
    var tab by rememberSaveable { mutableStateOf(CameraFilterTab.FILTERS) }
    var effect by rememberSaveable { mutableStateOf(PhotoEffect.entries.firstOrNull { effects.amount(it) > 0f } ?: PhotoEffect.NONE) }
    val options = remember(custom) { PhotoFilter.entries.filter { it != PhotoFilter.CUSTOM }.map { CameraFilterTile(it.name, it.label, it) } +
        custom.map { CameraFilterTile(it.id, it.label, PhotoFilter.CUSTOM, it) } }
    val assets = LocalContext.current.assets
    val previews by produceState<Map<String, ImageBitmap>>(emptyMap(), options) {
        value = withContext(Dispatchers.Default) {
            val source = decodeCameraReferencePhoto(assets, 2)
            if (source == null) emptyMap() else try { options.associate { option ->
                val target = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
                val side = minOf(source.width, source.height)
                Canvas(target).drawBitmap(source, Rect((source.width - side) / 2, (source.height - side) / 2, (source.width + side) / 2, (source.height + side) / 2), Rect(0, 0, 128, 128), Paint(Paint.FILTER_BITMAP_FLAG))
                val pixels = IntArray(128 * 128); target.getPixels(pixels, 0, 128, 0, 0, 128, 128)
                val matrix = PhotoFilters.coefficients(option.filter)
                val lut = PhotoFilters.lut(option.filter, option.custom?.lut)
                for (index in pixels.indices) pixels[index] = lut?.map(pixels[index], 1f) ?: PhotoFilters.apply(pixels[index], matrix)
                target.setPixels(pixels, 0, 128, 0, 0, 128, 128)
                option.id to target.asImageBitmap()
            } } finally { source.recycle() }
        }
    }
    val tile: @Composable (CameraFilterTile) -> Unit = { option ->
        val selected = if (option.filter == PhotoFilter.CUSTOM) filter == PhotoFilter.CUSTOM && option.id == selectedLut else option.filter == filter
        Surface(onClick = feedbackClick { if (option.custom != null) selectLut(option.id) else select(option.filter) }, enabled = enabled,
            modifier = Modifier.size(80.dp).testTag("camera-filter/" + option.id).semantics { this.selected = selected },
            shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null) {
            Box(Modifier.fillMaxSize()) {
                previews[option.id]?.let { Image(it, null, Modifier.fillMaxSize()) }
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(36.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .78f)))))
                Text(option.label, Modifier.align(Alignment.BottomStart).padding(7.dp), color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 2)
            }
        }
    }
    val add: @Composable () -> Unit = {
        Surface(onClick = feedbackClick(importLut), enabled = enabled, modifier = Modifier.size(80.dp).testTag("camera-import-lut"), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(Icons.Default.Add, null); Text("导入 LUT", style = MaterialTheme.typography.labelSmall) }
        }
    }
    val slider: @Composable (Modifier) -> Unit = { modifier ->
        val value = if (tab == CameraFilterTab.FILTERS) strength else effects.amount(effect)
        val change: (Float) -> Unit = if (tab == CameraFilterTab.FILTERS) changeStrength else { amount -> changeEffects(effects.with(effect, amount)) }
        val adjustable = enabled && if (tab == CameraFilterTab.FILTERS) filter != PhotoFilter.ORIGINAL else effect != PhotoEffect.NONE
        val semantics = modifier.testTag(if (tab == CameraFilterTab.FILTERS) "camera-filter-strength" else "camera-effect-strength")
            .semantics { contentDescription = if (tab == CameraFilterTab.FILTERS) "滤镜强度" else effect.label + "强度" }
        if (vertical) {
            val state = rememberSliderState(value, valueRange = 0f..1f)
            state.value = value; state.onValueChange = change
            VerticalSlider(state, semantics, enabled = adjustable, reverseDirection = true)
        } else Slider(value, change, semantics, enabled = adjustable)
    }
    val close: @Composable () -> Unit = { if (collapse != null) ToolButton("收起滤镜", if (vertical) Icons.Default.ChevronRight else Icons.Default.ExpandMore,
        collapse, Modifier.size(48.dp).testTag("camera-filter-collapse"), size = 40.dp, enabled = enabled,
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Transparent)) }
    val chooseEffect: (PhotoEffect) -> Unit = { effect = it; changeEffects(if (it == PhotoEffect.NONE) PhotoEffects.NONE else effects.with(it, effects.amount(it).takeIf { amount -> amount > 0f } ?: .6f)) }
    if (vertical) Row(Modifier.fillMaxHeight().padding(8.dp).testTag("camera-filters"), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(48.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            close(); slider(Modifier.width(48.dp).weight(1f).padding(vertical = 12.dp))
            CameraFilterTabs(tab, enabled, true) { tab = it }
        }
        if (tab == CameraFilterTab.EFFECTS) CameraEffectPicker(effect, effects, enabled, true, chooseEffect)
        else {
        LazyColumn(Modifier.width(80.dp).fillMaxHeight().testTag("camera-filter-list"), reverseLayout = true, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(options, key = { it.id }) { tile(it) }; item("import") { add() }
        }
        }
    } else Column(Modifier.fillMaxWidth().padding(8.dp).testTag("camera-filters")) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            CameraFilterTabs(tab, enabled, false) { tab = it }
            Spacer(Modifier.width(12.dp)); slider(Modifier.weight(1f).height(48.dp)); Spacer(Modifier.width(12.dp)); close()
        }
        if (tab == CameraFilterTab.EFFECTS) CameraEffectPicker(effect, effects, enabled, false, chooseEffect)
        else
        LazyRow(Modifier.testTag("camera-filter-list"), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(options, key = { it.id }) { tile(it) }; item("import") { add() } }
    }
}

private data class CameraFilterTile(val id: String, val label: String, val filter: PhotoFilter, val custom: CameraLutChoice? = null)
