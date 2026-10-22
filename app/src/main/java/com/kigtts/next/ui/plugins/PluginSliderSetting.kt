package com.kigtts.next.ui.plugins

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import com.kigtts.plugin.api.PluginControl
import java.util.Locale
import kotlin.math.roundToInt

/** Same native slider in dialogs and cards. Inline pages commit at release, avoiding busy flicker. */
@Composable
internal fun PluginSliderSetting(control: PluginControl.Slider, modifier: Modifier = Modifier, enabled: Boolean = true,
    continuous: Boolean = true, change: (String) -> Unit) {
    val min = control.min.takeIf(Float::isFinite) ?: 0f
    val max = control.max.takeIf { it.isFinite() && it > min } ?: (min + 1f)
    var value by remember(control.actionId, control.value) {
        mutableFloatStateOf(control.value.takeIf(Float::isFinite)?.coerceIn(min, max) ?: min)
    }
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(control.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            val label = if (value == value.roundToInt().toFloat()) value.roundToInt().toString()
                else "%.2f".format(Locale.ROOT, value).trimEnd('0').trimEnd('.')
            Text(label + control.suffix, style = MaterialTheme.typography.labelLarge)
        }
        Slider(value, { value = it; if (continuous) change(it.toString()) }, valueRange = min..max, enabled = enabled,
            steps = control.steps.coerceIn(0, 1000), onValueChangeFinished = { change(value.toString()) },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = control.title })
    }
}
