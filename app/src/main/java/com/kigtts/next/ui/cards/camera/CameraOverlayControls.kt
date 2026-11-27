@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards.camera

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.ToolButton
import com.kigtts.next.ui.components.keepColorsWhenLocked
import com.kigtts.next.ui.navigation.LocalPageActive
import com.kigtts.plugin.api.PolaroidFormat

internal val CameraFlash.icon get() = when (this) {
    CameraFlash.OFF -> Icons.Default.FlashOff
    CameraFlash.AUTO -> Icons.Default.FlashAuto
    CameraFlash.ON -> Icons.Default.FlashOn
}

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
    changeEffects: (com.kigtts.next.media.color.PhotoEffects) -> Unit = {}, aspectLabel: String? = null,
    showFilters: Boolean = true, showMotion: Boolean = motionButton != null, showFormat: Boolean = true,
    reserveBack: Boolean = false) {
    val copy = rememberCameraCopy()
    val colors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .88f),
        contentColor = MaterialTheme.colorScheme.onSurface).let { if (!LocalPageActive.current) it.keepColorsWhenLocked() else it }
    val formatButton: @Composable () -> Unit = {
        val shape = IconButtonDefaults.shapes(LocalCameraControlShape.current ?: RoundedCornerShape(22.dp), RoundedCornerShape(10.dp))
        ToolButton(copy.text("camera.status_aspect", "value" to (aspectLabel ?: copy.aspect(format, formats.size == 3))), if (aspectLabel != null) Icons.Default.AspectRatio else when (format) {
            PolaroidFormat.MINI -> Icons.Default.CropPortrait
            PolaroidFormat.WIDE -> Icons.Default.CropLandscape
            else -> Icons.Default.CropSquare
        }, cycleFormat, Modifier.testTag("camera-format"),
            size = 44.dp, shapes = shape, colors = colors, enabled = enabled)
    }
    val flashButton: @Composable () -> Unit = {
        val shape = IconButtonDefaults.shapes(LocalCameraControlShape.current ?: RoundedCornerShape(22.dp), RoundedCornerShape(10.dp))
            ToolButton(copy.text("camera.status_flash", "value" to copy.label(flash)), flash.icon,
                cycleFlash, Modifier.testTag("camera-flash"), size = 44.dp, shapes = shape,
                colors = if (flashAvailable) colors else colors.keepColorsWhenLocked(), enabled = enabled && flashAvailable)
    }
    val settingsButton: @Composable () -> Unit = {
        val shape = IconButtonDefaults.shapes(LocalCameraControlShape.current ?: RoundedCornerShape(22.dp), RoundedCornerShape(10.dp))
        CameraSettingsButton(openSettings,
            Modifier.testTag("camera-settings"),
            shapes = shape, colors = colors, enabled = enabled)
    }
    val filterButton: @Composable () -> Unit = {
        val shape = IconButtonDefaults.shapes(LocalCameraControlShape.current ?: RoundedCornerShape(22.dp), RoundedCornerShape(10.dp))
        ToolButton(copy.text("camera.filters"), Icons.Default.PhotoFilter, toggleFilters,
            Modifier.testTag("camera-filter-toggle")
                .semantics { stateDescription = if (filtersExpanded) copy.text("camera.expanded") else copy.text("camera.collapsed") },
            size = 44.dp, shapes = shape, colors = colors, enabled = enabled && showFilters)
    }
    CameraControlGroup(vertical, listOf(flashAvailable, showFilters, showMotion, showFormat, showSettings), reserveBack) { index ->
        when (index) {
            0 -> flashButton()
            1 -> filterButton()
            2 -> motionButton?.invoke()
            3 -> formatButton()
            4 -> settingsButton()
        }
    }
    CameraViewfinderPicker(filtersExpanded, vertical, layout) {
        CameraFilters(filter, enabled, selectFilter, vertical, strength, changeStrength, customLuts, selectedLut, selectLut, importLut,
            effects, changeEffects, toggleFilters)
    }
}
