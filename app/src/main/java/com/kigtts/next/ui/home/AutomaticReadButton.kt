package com.kigtts.next.ui.home

import com.kigtts.next.i18n.uiText

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.state.AppState
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.compactPlainIconColors
import com.kigtts.next.ui.components.iconLongAction

/** Both orientations share the Read button's opt-out and synthesis-service lifecycle. */
@Composable
internal fun AutomaticReadButton(state: AppState, model: AppViewModel) {
    val visible by model.synthesisGuidance.visible.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf(false) }
    if (picker) AudioDevicePicker(model.audioDevices) { picker = false }
    if (visible) Box(Modifier.size(48.dp).homePreviewRegion("header-start"), contentAlignment = Alignment.Center) {
        val progress = model.readingBakeProgress?.collectAsStateWithLifecycle()?.value
        IconButton(
            onClick = feedbackClick(model::toggleAutoPlayback),
            modifier = Modifier.fillMaxSize().testTag("auto-playback").iconLongAction(true) { picker = true },
            colors = compactPlainIconColors()) {
            Icon(if (state.autoPlayback) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                if (state.autoPlayback) uiText("subtitle.auto_off") else uiText("subtitle.auto_on"))
        }
        if (progress != null) {
            val animated = animateFloatAsState(progress, label = "speech-bake-progress")
            val label = uiText("subtitle.baking")
            CircularProgressIndicator(progress = { animated.value }, modifier = Modifier.size(42.dp).semantics { contentDescription = label }, strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.primary.copy(alpha = .2f))
        }
    }
}
