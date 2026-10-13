package com.kigtts.next.ui.cards.camera

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.MotionTextButton
import com.kigtts.next.ui.components.feedbackClick
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
            Text(zoomLabel(value), Modifier.testTag("camera-zoom-value"), style = MaterialTheme.typography.labelLarge)
        }
        val stop: @Composable (Float) -> Unit = { factor ->
            MotionTextButton(feedbackClick { zoom(factor) }, Modifier.testTag("camera-zoom-stop/" + factor.toInt()), enabled && range != null) {
                Text(factor.toInt().toString() + "×")
            }
        }
        if (vertical) Column(Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            label()
            if (range != null) BoxWithConstraints(Modifier.width(40.dp).weight(1f), contentAlignment = Alignment.Center) {
                Slider(value, zoom, Modifier.requiredWidth(maxHeight).height(40.dp).rotate(-90f)
                    .testTag("camera-zoom-slider").semantics { contentDescription = "变焦" },
                    enabled = enabled, valueRange = range)
            }
            stops.forEach { stop(it) }
        } else BoxWithConstraints {
            val showStops = maxWidth >= 300.dp
            Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            label()
            if (range != null) Slider(value, zoom, Modifier.weight(1f).height(40.dp)
                .testTag("camera-zoom-slider").semantics { contentDescription = "变焦" }, enabled = enabled, valueRange = range)
            // The slider stays usable even in a narrow rectangular viewfinder.
            if (range == null) Spacer(Modifier.width(8.dp))
            if (showStops) stops.forEach { stop(it) }
        } }
    }
}
