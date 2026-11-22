package com.kigtts.next.ui.plugins

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import com.kigtts.next.audio.*
import com.kigtts.plugin.api.PluginControl
import java.util.Locale

@Composable internal fun SpeechGainSlider(control: PluginControl.Slider, modifier: Modifier, enabled: Boolean, continuous: Boolean, change: (String) -> Unit) {
    var value by remember(control.value) { mutableFloatStateOf(control.value.takeIf(Float::isFinite)?.coerceIn(0f, 10f) ?: 1f) }
    val haptic = LocalHapticFeedback.current
    val gainDescription = com.kigtts.next.i18n.uiText("audio.gain_value", "value" to value.toString())
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(control.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text("%.2f".format(Locale.ROOT, value).trimEnd('0').trimEnd('.') + "×", style = MaterialTheme.typography.labelLarge)
        }
        Slider(speechGainPosition(value), { position ->
            val next = speechGainAt(position)
            if (next == 1f && value != 1f) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            value = next
            if (continuous) change(value.toString())
        }, enabled = enabled, onValueChangeFinished = { change(value.toString()) },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = control.title; stateDescription = gainDescription })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0", "1", "10").forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
