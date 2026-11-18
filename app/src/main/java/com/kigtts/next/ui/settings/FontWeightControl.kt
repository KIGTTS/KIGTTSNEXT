package com.kigtts.next.ui.settings

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kigtts.next.theme.FontWeightAxis
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.rememberDragFeedback
import kotlin.math.roundToInt

@Composable internal fun FontWeightControl(axis: FontWeightAxis, weight: Int, change: (Int) -> Unit) {
    val description = uiText("fonts.weight")
    val feedback = rememberDragFeedback()
    DisposableEffect(feedback) { onDispose { feedback.finish(cancelled = true) } }
    val selected = axis.clamp(weight)
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp).testTag("font-weight-control")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(uiText("fonts.weight"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(selected.toString(), style = MaterialTheme.typography.labelLarge)
                TextButton(feedbackClick { change(axis.default.roundToInt()) }, enabled = selected != axis.default.roundToInt()) { Text(uiText("camera.reset_default")) }
            }
            Slider(selected.toFloat(), { value ->
                val next = axis.clamp((value / 10).roundToInt() * 10)
                if (next != selected) { feedback.start(selected.toFloat()); feedback.move(next.toFloat()); change(next) }
            }, valueRange = axis.min..axis.max, onValueChangeFinished = { feedback.finish() },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = description; stateDescription = selected.toString() })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(uiText("fonts.light"), style = MaterialTheme.typography.labelMedium)
                Text(uiText("fonts.heavy"), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
