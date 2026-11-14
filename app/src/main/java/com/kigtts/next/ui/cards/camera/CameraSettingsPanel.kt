@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kigtts.next.ui.cards.camera

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kigtts.next.media.CameraPhotoEncoding
import com.kigtts.next.ui.components.ModalPageTransition
import com.kigtts.next.ui.components.feedbackClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun CameraSettingsPanel(options: CameraOptions, flashAvailable: Boolean, enabled: Boolean = true,
    availableProcessing: Set<CameraProcessing> = setOf(CameraProcessing.STANDARD), manual: CameraManualController? = null,
    watermarkPreview: () -> Bitmap? = { null }, watermarkStore: (() -> Unit)? = null,
    exposureWarningAvailable: Boolean = true) {
    if (!options.settingsVisible) return
    var watermarkPage by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<com.kigtts.next.cards.CameraWatermarkAsset?>(null) }
    var lastEdited by remember { mutableStateOf<com.kigtts.next.cards.CameraWatermarkAsset?>(null) }
    var snapshot by remember { mutableStateOf<Bitmap?>(null) }
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
                snapshot = watermarkPreview()
                watermarkPage = true
            }
        }
    }
}

@Composable private fun CameraSettingsGrid(options: CameraOptions, flashAvailable: Boolean, enabled: Boolean,
    availableProcessing: Set<CameraProcessing>, manual: CameraManualController?, exposureWarningAvailable: Boolean,
    openWatermark: () -> Unit) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Tune, null, Modifier.size(22.dp))
                Text("相机设置", style = MaterialTheme.typography.titleLarge)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CameraTile("画幅", options.framingAspect.label, Icons.Default.AspectRatio,
                    enabled && options.mode != CameraMode.SCAN, Modifier.weight(1f)) {
                    options.chooseAspect(CameraAspect.entries[(options.aspect.ordinal + 1) % CameraAspect.entries.size]) }
                CameraTile("倒计时", if (options.timerSeconds == 0) "关闭" else "${options.timerSeconds} 秒", Icons.Default.Timer, enabled, Modifier.weight(1f)) {
                    options.setTimer(CameraOptions.TIMERS[(CameraOptions.TIMERS.indexOf(options.timerSeconds) + 1) % CameraOptions.TIMERS.size]) }
                CameraTile("辅助线", options.grid.label, Icons.Default.Grid3x3, enabled, Modifier.weight(1f)) {
                    options.setGrid(CameraGrid.entries[(options.grid.ordinal + 1) % CameraGrid.entries.size]) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CameraTile("水平仪", if (options.level) "开启" else "关闭", Icons.Default.Straighten, enabled, Modifier.weight(1f)) { options.setLevel(!options.level) }
                if (flashAvailable) CameraTile("闪光灯", options.flash.label, Icons.Default.FlashOn, enabled, Modifier.weight(1f)) {
                    options.setFlash(CameraFlash.entries[(options.flash.ordinal + 1) % CameraFlash.entries.size]) }
                val processing = availableProcessing.toList()
                CameraTile("照片处理", if (options.mode == CameraMode.PRO) CameraProcessing.STANDARD.label else options.processing.label,
                    Icons.Default.AutoAwesome, enabled && options.mode == CameraMode.PHOTO, Modifier.weight(1f)) {
                    options.setProcessing(processing[(processing.indexOf(options.processing).coerceAtLeast(0) + 1) % processing.size]) }
            }
            if (options.mode.takesPhotos) {
                CameraOutputTiles(options, enabled, openWatermark)
                if (options.mode == CameraMode.PRO) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CameraTile("曝光警示", if (!exposureWarningAvailable) "当前镜头不可用" else if (options.exposureWarning) "开启" else "关闭",
                            Icons.Default.WarningAmber, enabled && exposureWarningAvailable, Modifier.weight(1f)) {
                            options.setExposureWarning(!options.exposureWarning)
                        }
                        Spacer(Modifier.weight(2f))
                    }
                }
                manual?.let { CameraManualPanel(it, enabled) }
            }
        }
}

@Composable private fun CameraOutputTiles(options: CameraOptions, enabled: Boolean, openWatermark: () -> Unit) {
    val heifAvailable by produceState<Boolean?>(null) {
        value = withContext(Dispatchers.IO) { CameraPhotoEncoding.heifAvailable }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CameraTile("照片格式", options.encoding.label, Icons.Default.Image,
            enabled && (heifAvailable == true || options.encoding == CameraPhotoEncoding.HEIF), Modifier.weight(1f)) {
            options.chooseEncoding(if (options.encoding == CameraPhotoEncoding.JPEG) CameraPhotoEncoding.HEIF else CameraPhotoEncoding.JPEG)
        }
        CameraTile("边框水印", options.watermarkLabel, Icons.Default.FilterFrames,
            enabled, Modifier.weight(1f), openWatermark)
        Spacer(Modifier.weight(1f))
    }
    val hint = when {
        heifAvailable == false -> "此设备不支持保存 HEIF"
        options.motion -> "选择 HEIF 会关闭实况"
        options.encoding == CameraPhotoEncoding.HEIF -> "实况照片使用 JPEG"
        else -> null
    }
    if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun CameraTile(title: String, value: String, icon: ImageVector, enabled: Boolean, modifier: Modifier, click: () -> Unit) {
    Surface(onClick = feedbackClick(click), modifier = modifier, enabled = enabled, shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, Modifier.size(26.dp)); Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}
