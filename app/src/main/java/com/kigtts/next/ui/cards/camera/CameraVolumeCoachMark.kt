package com.kigtts.next.ui.cards.camera

import android.view.Surface
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.i18n.uiText
import com.kigtts.next.ui.navigation.excludeCameraPull
import com.kigtts.next.ui.onboarding.CoachMarkBubble
import kotlinx.coroutines.delay

@Composable
internal fun BoxScope.CameraVolumeCoachMark(options: CameraOptions, eligible: Boolean, vertical: Boolean, rotation: Int) {
    var visible by remember(options) { mutableStateOf(false) }
    LaunchedEffect(eligible, options) {
        if (!eligible) visible = false
        else if (!options.volumeHintSeen()) {
            delay(450)
            visible = true
        }
    }
    if (!eligible || !visible) return
    val alignment: Alignment
    val position: Modifier
    when {
        vertical && rotation == Surface.ROTATION_270 -> {
            alignment = Alignment.BottomEnd
            position = Modifier.padding(end = 152.dp, bottom = 12.dp)
        }
        vertical -> {
            alignment = Alignment.TopStart
            position = Modifier.padding(start = 64.dp, end = 12.dp, top = 12.dp)
        }
        rotation == Surface.ROTATION_180 -> {
            alignment = Alignment.BottomStart
            position = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 114.dp)
        }
        else -> {
            alignment = Alignment.TopEnd
            position = Modifier.padding(start = 12.dp, end = 12.dp, top = 74.dp)
        }
    }
    CoachMarkBubble(uiText("camera.volume_shutter_tip"), uiText("camera.volume_shutter_tip_body"),
        Modifier.align(alignment).then(position).widthIn(max = 268.dp).testTag("camera-volume-tip").excludeCameraPull(),
        onPresented = options::markVolumeHintSeen, dismiss = { visible = false })
}
