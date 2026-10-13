@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards.camera

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.util.Locale

internal fun cameraZoomRange(minimum: Float, maximum: Float): ClosedFloatingPointRange<Float>? =
    (minimum..maximum).takeIf { minimum.isFinite() && maximum.isFinite() && minimum > 0f && maximum > minimum }

/** Stops come solely from the bound camera's reported range, never assumed physical lenses. */
internal fun cameraZoomStops(range: ClosedFloatingPointRange<Float>) = listOf(1f, 2f).filter { it in range }
private fun zoomLabel(value: Float): String = String.format(Locale.ROOT, "%.1f×", value)

@Composable
internal fun CameraZoomRail(ratio: Float, minimum: Float, maximum: Float, enabled: Boolean,
    zoom: (Float) -> Unit, vertical: Boolean, modifier: Modifier = Modifier) {
    val range = cameraZoomRange(minimum, maximum)
    val value = if (range != null) ratio.takeIf(Float::isFinite)?.coerceIn(range) ?: range.start else minimum
    val stops = range?.let(::cameraZoomStops).orEmpty()
    Surface(modifier.testTag("camera-zoom-rail"), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .88f)) {
        val label: @Composable () -> Unit = {
            CameraZoomButton(zoomLabel(value), "当前变焦 ${zoomLabel(value)}，点按重置", enabled && range != null,
                { range?.let { zoom(if (1f in it) 1f else it.start) } }, Modifier.testTag("camera-zoom-current"), "camera-zoom-value")
        }
        val stop: @Composable (Float) -> Unit = { factor ->
            CameraZoomButton(factor.toInt().toString() + "×", "变焦 ${factor.toInt()} 倍", enabled && range != null,
                { zoom(factor) }, Modifier.testTag("camera-zoom-stop/" + factor.toInt()))
        }
        if (vertical) BoxWithConstraints {
            val showStops = maxHeight >= (48 * (2 + stops.size)).dp
            Column(Modifier.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            label()
            if (range != null) {
                val state = rememberSliderState(value, valueRange = range)
                state.value = value
                state.onValueChange = { selected -> state.value = selected; zoom(selected) }
                VerticalSlider(state, Modifier.width(48.dp).weight(1f).cameraSliderPressFeedback()
                    .testTag("camera-zoom-slider").semantics { contentDescription = "变焦" }, enabled = enabled,
                    reverseDirection = true)
            }
            if (showStops) stops.forEach { stop(it) }
            }
        } else BoxWithConstraints {
            val showStops = maxWidth >= 300.dp
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            label()
            if (range != null) Slider(value, zoom, Modifier.weight(1f).height(48.dp).cameraSliderPressFeedback()
                .testTag("camera-zoom-slider").semantics { contentDescription = "变焦" }, enabled = enabled, valueRange = range)
            // The slider stays usable even in a narrow rectangular viewfinder.
            if (range == null) Spacer(Modifier.width(8.dp))
            if (showStops) stops.forEach { stop(it) }
        } }
    }
}
