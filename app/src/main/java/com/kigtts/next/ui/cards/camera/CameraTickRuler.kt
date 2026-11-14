@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kigtts.next.ui.cards.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Shared stepped slider for EV, shutter, ISO, white balance and focus. */
@Composable
internal fun CameraTickRuler(range: IntRange, value: Int, label: (Int) -> String, description: String,
    tag: String, enabled: Boolean, change: (Int) -> Unit, modifier: Modifier = Modifier,
    valueLabel: String = label(value), emphasis: Int? = null) {
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label(range.first), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall)
            Text(valueLabel, Modifier.weight(1.4f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = colors.primary)
            Text(label(range.last), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
        }
        if (range.first == range.last) { Spacer(Modifier.height(27.dp)); return@Column }
        val haptic = LocalHapticFeedback.current
        val slider = key(range) { rememberSliderState(value.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0), valueRange = range.first.toFloat()..range.last.toFloat()) }
        slider.value = value.toFloat()
        var lastTick = value
        val count = range.last - range.first
        val ticks = remember(range, emphasis) {
            ((range step (count / 32 + 1)).toList() + listOfNotNull(emphasis?.takeIf { it in range }) + range.last).distinct()
        }
        slider.onValueChange = {
            val next = it.roundToInt()
            if (next != lastTick) {
                lastTick = next
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                change(next)
            }
        }
        Slider(slider, Modifier.fillMaxWidth().height(27.dp).cameraSliderPressFeedback()
            .testTag(tag).semantics { contentDescription = description },
            enabled = enabled, thumb = {
                Canvas(Modifier.size(3.dp, 21.dp)) {
                    drawLine(colors.primary, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), size.width)
                }
            }, track = {
                Canvas(Modifier.fillMaxWidth().height(22.dp)) {
                    ticks.forEach { tick ->
                        val x = size.width * (tick - range.first) / count
                        val strong = tick == emphasis || tick == range.first || tick == range.last
                        val radius = if (strong) .42f else .23f
                        drawLine(colors.onSurface.copy(alpha = if (strong) .7f else .38f),
                            Offset(x, size.height * (.5f - radius)), Offset(x, size.height * (.5f + radius)), 1.dp.toPx())
                    }
                }
            })
    }
}
