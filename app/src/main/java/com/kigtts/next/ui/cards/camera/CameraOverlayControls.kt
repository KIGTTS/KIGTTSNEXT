@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards.camera

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.ToolButton
import com.kigtts.next.ui.components.keepColorsWhenLocked
import com.kigtts.next.ui.navigation.LocalPageActive
import com.kigtts.next.ui.navigation.excludeCameraPull
import com.kigtts.plugin.api.PolaroidFormat

/** Portrait actions stay at the top right; landscape actions gather at the bottom left. */
@Composable
internal fun BoxScope.CameraOverlayControls(format: PolaroidFormat, formats: List<PolaroidFormat>,
    filter: PhotoFilter, enabled: Boolean, vertical: Boolean, filtersExpanded: Boolean,
    cycleFormat: () -> Unit, openSettings: () -> Unit, toggleFilters: () -> Unit, selectFilter: (PhotoFilter) -> Unit,
    flash: CameraFlash = CameraFlash.OFF, flashAvailable: Boolean = false, cycleFlash: () -> Unit = {},
    strength: Float = 1f, changeStrength: (Float) -> Unit = {}, layout: CameraChromeLayout? = null,
    showSettings: Boolean = true, customLuts: List<CameraLutChoice> = emptyList(), selectedLut: String? = null,
    selectLut: (String) -> Unit = {}, importLut: () -> Unit = {}, motionButton: @Composable (() -> Unit)? = null,
    effects: com.kigtts.next.media.color.PhotoEffects = com.kigtts.next.media.color.PhotoEffects.NONE,
    changeEffects: (com.kigtts.next.media.color.PhotoEffects) -> Unit = {}) {
    val colors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .88f),
        contentColor = MaterialTheme.colorScheme.onSurface).let { if (!LocalPageActive.current) it.keepColorsWhenLocked() else it }
    val formatButton: @Composable () -> Unit = {
        val shape = IconButtonDefaults.shapes(LocalCameraControlShape.current ?: RoundedCornerShape(22.dp), RoundedCornerShape(10.dp))
        ToolButton("画幅：" + format.cameraLabel(formats.size == 3) + "，点按切换", when (format) {
            PolaroidFormat.MINI -> Icons.Default.CropPortrait
            PolaroidFormat.WIDE -> Icons.Default.CropLandscape
            else -> Icons.Default.CropSquare
        }, cycleFormat, Modifier.testTag("camera-format"),
            size = 44.dp, shapes = shape, colors = colors, enabled = enabled)
    }
    val flashButton: @Composable () -> Unit = {
        val shape = IconButtonDefaults.shapes(LocalCameraControlShape.current ?: RoundedCornerShape(22.dp), RoundedCornerShape(10.dp))
            ToolButton("闪光灯：" + flash.label + "，点按切换", when (flash) {
                CameraFlash.OFF -> Icons.Default.FlashOff
                CameraFlash.AUTO -> Icons.Default.FlashAuto
                CameraFlash.ON -> Icons.Default.FlashOn
            }, cycleFlash, Modifier.testTag("camera-flash"), size = 44.dp, shapes = shape,
                colors = if (flashAvailable) colors else colors.keepColorsWhenLocked(), enabled = enabled && flashAvailable)
    }
    val settingsButton: @Composable () -> Unit = {
        val shape = IconButtonDefaults.shapes(LocalCameraControlShape.current ?: RoundedCornerShape(22.dp), RoundedCornerShape(10.dp))
        if (showSettings) ToolButton("相机设置", Icons.Default.Tune, openSettings,
            Modifier.testTag("camera-settings"), size = 44.dp,
            shapes = shape, colors = colors, enabled = enabled)
    }
    val filterButton: @Composable () -> Unit = {
        val shape = IconButtonDefaults.shapes(LocalCameraControlShape.current ?: RoundedCornerShape(22.dp), RoundedCornerShape(10.dp))
        ToolButton("滤镜", Icons.Default.PhotoFilter, toggleFilters,
            Modifier.testTag("camera-filter-toggle")
                .semantics { stateDescription = if (filtersExpanded) "已展开" else "已收起" },
            size = 44.dp, shapes = shape, colors = colors, enabled = enabled)
    }
    val items = listOfNotNull(flashButton, filterButton, motionButton, formatButton,
        settingsButton.takeIf { showSettings })
    CameraControlGroup(vertical, items.indices.map { it != 0 || flashAvailable }) { index -> items[index]() }
    Box(if (layout != null) Modifier.align(Alignment.TopStart).cameraChromeBounds(layout.photo).clip(RoundedCornerShape(16.dp))
        else Modifier.matchParentSize()) {
    // Keep both rails accessible, including while chrome is moving outside the viewfinder.
    val zoomClearance = if (layout == null) 60f else if (vertical)
        (layout.photo.right - layout.zoom.left + 8f).coerceAtLeast(8f)
    else (layout.photo.bottom - layout.zoom.top + 8f).coerceAtLeast(8f)
    AnimatedVisibility(filtersExpanded,
        modifier = if (vertical) Modifier.align(Alignment.CenterEnd).width((144f + zoomClearance).dp).fillMaxHeight()
            else Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        enter = fadeIn() + if (vertical) expandHorizontally(expandFrom = Alignment.End) else expandVertically(expandFrom = Alignment.Bottom),
        exit = fadeOut() + if (vertical) shrinkHorizontally(shrinkTowards = Alignment.End) else shrinkVertically(shrinkTowards = Alignment.Bottom)) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = androidx.compose.ui.graphics.RectangleShape) {
            Box(if (vertical) Modifier.padding(end = zoomClearance.dp) else Modifier.padding(bottom = zoomClearance.dp)) {
                CameraFilters(filter, enabled, selectFilter, vertical, strength, changeStrength, customLuts, selectedLut, selectLut, importLut,
                    effects, changeEffects, toggleFilters)
            }
        }
    }
    }
}
