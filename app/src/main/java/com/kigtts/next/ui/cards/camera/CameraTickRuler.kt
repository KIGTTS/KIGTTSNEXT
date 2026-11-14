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
import kotlin.math.abs
import kotlin.math.roundToInt

/** Numeric ranges move continuously; true camera presets remain discrete. */
@Composable
internal fun CameraTickRuler(range: IntRange, value: Int, label: (Int) -> String, description: String,
    tag: String, enabled: Boolean, change: (Int) -> Unit, modifier: Modifier = Modifier,
    valueLabel: String = label(value), emphasis: Int? = null, snapToEmphasis: Boolean = false) {
    CameraContinuousRuler(range.first.toFloat()..range.last.toFloat(), value.toFloat(),
        { label(it.roundToInt()) }, description, tag, enabled, { change(it.roundToInt()) }, modifier,
        valueLabel, emphasis?.toFloat(), snapToEmphasis, discrete = !snapToEmphasis)
}

@Composable
internal fun CameraContinuousRuler(range: ClosedFloatingPointRange<Float>, value: Float, label: (Float) -> String,
    description: String, tag: String, enabled: Boolean, change: (Float) -> Unit, modifier: Modifier = Modifier,
    valueLabel: String = label(value), emphasis: Float? = null, snapToEmphasis: Boolean = false,
    discrete: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label(range.start), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall)
            Text(valueLabel, Modifier.weight(1.4f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = colors.primary)
            Text(label(range.endInclusive), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
        }
        if (range.start == range.endInclusive) { Spacer(Modifier.height(27.dp)); return@Column }
        val haptic = LocalHapticFeedback.current
        val count = range.endInclusive - range.start
        val slider = key(range, discrete) { rememberSliderState(value,
            steps = if (discrete) (count.roundToInt() - 1).coerceAtLeast(0) else 0, valueRange = range) }
        // Keep the thumb between hardware indices (Camera2 EV only accepts whole steps).
        var dragPosition by remember(range) { mutableStateOf<Float?>(null) }
        var lastDetent by remember(range) { mutableStateOf<Float?>(null) }
        LaunchedEffect(value, enabled) { if (!enabled) dragPosition = null }
        slider.value = dragPosition ?: value
        slider.onValueChangeFinished = { dragPosition = null; lastDetent = null }
        val ticks = remember(range, emphasis, discrete) {
            val n = if (discrete) count.roundToInt().coerceIn(1, 32) else 32
            ((0..n).map { range.start + it * count / n } + listOfNotNull(emphasis?.takeIf { it in range })).distinct()
        }
        slider.onValueChange = { raw ->
            val next = cameraRulerPosition(raw, range, emphasis.takeIf { snapToEmphasis })
            val detent = if (discrete) next else emphasis?.takeIf { next == it }
            if (detent != null && lastDetent != detent && (snapToEmphasis || detent != value)) {
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
            lastDetent = detent
            if (snapToEmphasis) dragPosition = next
            change(next)
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
                        if (snapToEmphasis && emphasis != null && tick != emphasis && abs(tick - emphasis) < count / 64) return@forEach
                        val x = size.width * (tick - range.start) / count
                        val strong = tick == emphasis || tick == range.start || tick == range.endInclusive
                        val radius = if (strong) .42f else .23f
                        drawLine(colors.onSurface.copy(alpha = if (strong) .7f else .38f),
                            Offset(x, size.height * (.5f - radius)), Offset(x, size.height * (.5f + radius)), 1.dp.toPx())
                    }
                }
            })
    }
}


internal fun cameraRulerPosition(value: Float, range: ClosedFloatingPointRange<Float>, neutral: Float?): Float {
    val bounded = value.coerceIn(range)
    val radius = (range.endInclusive - range.start) * .035f
    return if (neutral != null && neutral in range && abs(bounded - neutral) <= radius) neutral else bounded
}
