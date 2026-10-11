package com.kigtts.next.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.AppViewModel

/** Retain the strip through exit, with stable toolbar height and immediate input cancellation. */
@Composable
internal fun CursorTrackSlot(editing: Boolean, model: AppViewModel, reveal: Float, modifier: Modifier) {
    Box(modifier.height(32.dp).clipToBounds().testTag("cursor-slot"), contentAlignment = Alignment.Center) {
        if (editing || reveal > 0f) {
            Box(Modifier.fillMaxWidth(.6f + .4f * reveal).graphicsLayer { alpha = reveal }) {
                CursorTrack(model, Modifier.fillMaxWidth(), enabled = editing)
            }
        }
        if (!editing) Spacer(Modifier.fillMaxSize().testTag("editor-bottom-reserved"))
    }
}
