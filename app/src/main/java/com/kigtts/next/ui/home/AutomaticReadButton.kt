package com.kigtts.next.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.state.AppState
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.components.compactPlainIconColors
import com.kigtts.next.ui.components.feedbackClick

/** Both orientations share the Read button's opt-out and synthesis-service lifecycle. */
@Composable
internal fun AutomaticReadButton(state: AppState, model: AppViewModel) {
    val visible by model.synthesisGuidance.visible.collectAsStateWithLifecycle()
    if (visible) IconButton(feedbackClick(model::toggleAutoPlayback),
        Modifier.testTag("auto-playback").homePreviewRegion("header-start"), colors = compactPlainIconColors()) {
        Icon(if (state.autoPlayback) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
            if (state.autoPlayback) "关闭自动播放与朗读" else "开启自动播放与朗读")
    }
}
