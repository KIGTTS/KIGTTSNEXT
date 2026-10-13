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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.ToolButton
import com.kigtts.next.ui.navigation.excludeCameraPull
import com.kigtts.plugin.api.PolaroidFormat

/** Camera chrome follows the portrait geometry; landscape moves the top edge to the left. */
@Composable
internal fun BoxScope.CameraOverlayControls(format: PolaroidFormat, formats: List<PolaroidFormat>,
    filter: PhotoFilter, enabled: Boolean, vertical: Boolean, filtersExpanded: Boolean,
    cycleFormat: () -> Unit, openSettings: () -> Unit, toggleFilters: () -> Unit, selectFilter: (PhotoFilter) -> Unit,
    flash: CameraFlash = CameraFlash.OFF, flashAvailable: Boolean = false, cycleFlash: () -> Unit = {},
    strength: Float = 1f, changeStrength: (Float) -> Unit = {}) {
    val shape = IconButtonDefaults.shapes(RoundedCornerShape(22.dp), RoundedCornerShape(10.dp))
    val colors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .88f),
        contentColor = MaterialTheme.colorScheme.onSurface)
    val items: @Composable () -> Unit = {
        ToolButton("画幅：" + format.cameraLabel(formats.size == 3) + "，点按切换", when (format) {
            PolaroidFormat.MINI -> Icons.Default.CropPortrait
            PolaroidFormat.WIDE -> Icons.Default.CropLandscape
            else -> Icons.Default.CropSquare
        }, cycleFormat, Modifier.testTag("camera-format"),
            size = 44.dp, shapes = shape, colors = colors, enabled = enabled)
        AnimatedVisibility(flashAvailable,
            enter = fadeIn() + if (vertical) expandVertically() else expandHorizontally(),
            exit = fadeOut() + if (vertical) shrinkVertically() else shrinkHorizontally()) {
            ToolButton("闪光灯：" + flash.label + "，点按切换", when (flash) {
                CameraFlash.OFF -> Icons.Default.FlashOff
                CameraFlash.AUTO -> Icons.Default.FlashAuto
                CameraFlash.ON -> Icons.Default.FlashOn
            }, cycleFlash, Modifier.testTag("camera-flash"), size = 44.dp, shapes = shape, colors = colors, enabled = enabled)
        }
        ToolButton("相机设置", Icons.Default.Tune, openSettings,
            Modifier.testTag("camera-settings"), size = 44.dp,
            shapes = shape, colors = colors, enabled = enabled)
        ToolButton(if (filtersExpanded) "收起滤镜" else "滤镜", Icons.Default.PhotoFilter, toggleFilters,
            Modifier.testTag("camera-filter-toggle")
                .semantics { stateDescription = if (filtersExpanded) "已展开" else "已收起" },
            size = 44.dp, shapes = shape, colors = colors, enabled = enabled)
    }
    if (vertical) Column(Modifier.align(Alignment.CenterStart).width(56.dp).fillMaxHeight().padding(vertical = 6.dp).excludeCameraPull(),
        verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally) { items() }
    else Row(Modifier.align(Alignment.TopCenter).height(56.dp).fillMaxWidth().padding(horizontal = 6.dp).excludeCameraPull(),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { items() }
    AnimatedVisibility(filtersExpanded,
        modifier = if (vertical) Modifier.align(Alignment.CenterEnd).widthIn(max = 104.dp).fillMaxHeight()
            else Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        enter = fadeIn() + if (vertical) expandHorizontally(expandFrom = Alignment.End) else expandVertically(expandFrom = Alignment.Bottom),
        exit = fadeOut() + if (vertical) shrinkHorizontally(shrinkTowards = Alignment.End) else shrinkVertically(shrinkTowards = Alignment.Bottom)) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .92f), shape = MaterialTheme.shapes.large) {
            CameraFilters(filter, enabled, selectFilter, vertical, strength, changeStrength)
        }
    }
}
