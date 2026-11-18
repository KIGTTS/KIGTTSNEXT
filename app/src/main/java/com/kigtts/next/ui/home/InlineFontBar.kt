@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kigtts.next.ui.home

import com.kigtts.next.i18n.uiText

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import com.kigtts.next.ui.components.ToolButton
import com.kigtts.next.ui.components.animateEnabled
import com.kigtts.next.ui.navigation.LocalPageActive

@Composable
fun InlineFontBar(label: String, value: Float, range: ClosedFloatingPointRange<Float>,
    change: (Float) -> Unit, dismiss: () -> Unit, modifier: Modifier = Modifier, valueDescription: String? = null) {
    val active = LocalPageActive.current
    BackHandler(active) { dismiss() }
    val source = remember { MutableInteractionSource() }
    val colors = SliderDefaults.colors().animateEnabled(active)
    Row(modifier.height(32.dp).testTag("inline-" + label), verticalAlignment = Alignment.CenterVertically) {
        Slider(value, change, valueRange = range, enabled = active,
            colors = colors,
            modifier = Modifier.weight(1f).height(32.dp).semantics {
                contentDescription = label + "滑条"
                if (valueDescription != null) stateDescription = valueDescription
            }, interactionSource = source,
            thumb = { SliderDefaults.Thumb(source, colors = colors, enabled = active, modifier = Modifier.testTag("font-thumb-" + label), thumbSize = DpSize(4.dp, 24.dp)) },
            track = { SliderDefaults.Track(it, colors = colors, enabled = active, trackCornerSize = 4.dp, modifier = Modifier.height(8.dp)) })
        Spacer(Modifier.width(4.dp))
        ToolButton(uiText("accessibility.finish_adjusting", "label" to label), Icons.Default.Check, { if (active) dismiss() })
    }
}
