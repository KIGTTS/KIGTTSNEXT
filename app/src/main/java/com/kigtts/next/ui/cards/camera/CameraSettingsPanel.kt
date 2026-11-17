@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kigtts.next.ui.cards.camera

import com.kigtts.next.i18n.uiText
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kigtts.next.media.CameraPhotoEncoding
import com.kigtts.next.ui.components.ModalPageTransition
import com.kigtts.next.ui.components.CornerNeighbors
import com.kigtts.next.ui.components.connectedShape
import com.kigtts.next.ui.components.feedbackClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun CameraSettingsPanel(options: CameraOptions, flashAvailable: Boolean, enabled: Boolean = true,
    availableProcessing: Set<CameraProcessing> = setOf(CameraProcessing.STANDARD), manual: CameraManualController? = null,
    watermarkStore: (() -> Unit)? = null,
    exposureWarningAvailable: Boolean = true) {
    if (!options.settingsVisible) return
    var watermarkPage by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<com.kigtts.next.cards.CameraWatermarkAsset?>(null) }
    var lastEdited by remember { mutableStateOf<com.kigtts.next.cards.CameraWatermarkAsset?>(null) }
    val snapshot = rememberCameraReferencePhoto(watermarkPage)
    ModalBottomSheet(options::hideSettings, containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = Color.Black.copy(alpha = .26f),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !watermarkPage)) {
        BackHandler(watermarkPage) { if (editing != null) editing = null else watermarkPage = false }
        val page = if (watermarkPage) if (editing != null) 2 else 1 else 0
        ModalPageTransition(page, { it }) { shown ->
            if (shown == 2) lastEdited?.let { asset ->
                CameraWatermarkEditor(asset, options, snapshot, enabled) { editing = null }
            }
            else if (shown == 1) CameraWatermarkPage(options, snapshot, enabled,
                watermarkStore?.let { store -> { options.hideSettings(); store() } },
                { lastEdited = it; editing = it }) { watermarkPage = false }
            else CameraSettingsGrid(options, flashAvailable, enabled, availableProcessing, manual, exposureWarningAvailable) {
                watermarkPage = true
            }
        }
    }
}

@Composable internal fun CameraSettingsGrid(options: CameraOptions, flashAvailable: Boolean, enabled: Boolean,
    availableProcessing: Set<CameraProcessing>, manual: CameraManualController?, exposureWarningAvailable: Boolean,
    openWatermark: () -> Unit) {
    val copy = rememberCameraCopy()
    val heifAvailable by produceState<Boolean?>(null) {
        value = withContext(Dispatchers.IO) { CameraPhotoEncoding.heifAvailable }
    }
    val tiles = buildList {
        add(CameraSettingTile(copy.text("camera.aspect"), copy.label(options.framingAspect), Icons.Default.AspectRatio,
            enabled && options.mode != CameraMode.SCAN) {
            options.chooseAspect(CameraAspect.entries[(options.aspect.ordinal + 1) % CameraAspect.entries.size])
        })
        add(CameraSettingTile(copy.text("camera.timer"), if (options.timerSeconds == 0) copy.text("option.off")
            else copy.text("camera.timer_seconds", "seconds" to options.timerSeconds.toString()), Icons.Default.Timer, enabled) {
            options.setTimer(CameraOptions.TIMERS[(CameraOptions.TIMERS.indexOf(options.timerSeconds) + 1) % CameraOptions.TIMERS.size])
        })
        add(CameraSettingTile(copy.text("camera.grid"), copy.label(options.grid), Icons.Default.Grid3x3, enabled) {
            options.setGrid(CameraGrid.entries[(options.grid.ordinal + 1) % CameraGrid.entries.size])
        })
        add(CameraSettingTile(copy.text("camera.level"), copy.toggle(options.level), Icons.Default.Straighten, enabled) { options.setLevel(!options.level) })
        if (flashAvailable) add(CameraSettingTile(copy.text("camera.flash"), copy.label(options.flash), Icons.Default.FlashOn, enabled) {
            options.setFlash(CameraFlash.entries[(options.flash.ordinal + 1) % CameraFlash.entries.size])
        })
        val processing = availableProcessing.toList().ifEmpty { listOf(CameraProcessing.STANDARD) }
        add(CameraSettingTile(copy.text("camera.process"), copy.label(if (options.mode == CameraMode.PRO) CameraProcessing.STANDARD else options.processing),
            Icons.Default.AutoAwesome, enabled && options.mode == CameraMode.PHOTO) {
            options.setProcessing(processing[(processing.indexOf(options.processing).coerceAtLeast(0) + 1) % processing.size])
        })
        if (options.mode.takesPhotos) {
            add(CameraSettingTile(copy.text("camera.encoding"), options.encoding.label, Icons.Default.Image,
                enabled && (heifAvailable == true || options.encoding == CameraPhotoEncoding.HEIF)) {
                options.chooseEncoding(if (options.encoding == CameraPhotoEncoding.JPEG) CameraPhotoEncoding.HEIF else CameraPhotoEncoding.JPEG)
            })
            add(CameraSettingTile(copy.text("camera.frame_watermark"),
                options.activeWatermark?.takeIf { options.watermarkEnabled }?.let(copy::label) ?: copy.text("option.off"),
                Icons.Default.FilterFrames, enabled, openWatermark))
            if (options.mode == CameraMode.PRO) add(CameraSettingTile(copy.text("camera.exposure_warning"),
                if (exposureWarningAvailable) copy.toggle(options.exposureWarning) else copy.text("camera.unavailable_lens"),
                Icons.Default.WarningAmber, enabled && exposureWarningAvailable) { options.setExposureWarning(!options.exposureWarning) })
        }
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Tune, null, Modifier.size(22.dp))
            Text(uiText("camera.settings"), style = MaterialTheme.typography.titleLarge)
        }
        CameraTileGrid(tiles)
        if (options.mode.takesPhotos) {
            val hint = when {
                heifAvailable == false -> uiText("camera.no_heif")
                options.motion -> uiText("camera.heif_motion")
                options.encoding == CameraPhotoEncoding.HEIF -> uiText("camera.motion_jpeg")
                else -> null
            }
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            manual?.let { CameraManualPanel(it, enabled) }
        }
    }
}

private data class CameraSettingTile(val title: String, val value: String, val icon: ImageVector,
    val enabled: Boolean, val click: () -> Unit)

@Composable private fun CameraTileGrid(tiles: List<CameraSettingTile>) {
    val rows = tiles.chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEachIndexed { y, row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEachIndexed { x, tile ->
                    val neighbors = CornerNeighbors(start = x > 0, end = x < row.lastIndex,
                        above = rows.getOrNull(y - 1)?.indices?.contains(x) == true,
                        below = rows.getOrNull(y + 1)?.indices?.contains(x) == true)
                    Surface(onClick = feedbackClick(tile.click), modifier = Modifier.weight(1f).fillMaxHeight(),
                        enabled = tile.enabled, shape = connectedShape(neighbors),
                        color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
                            Icon(tile.icon, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(tile.title, Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                            Text(tile.value, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
