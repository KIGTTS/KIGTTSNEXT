package com.kigtts.next.ui.cards.camera

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun BoxScope.CameraCountdownOverlay(seconds: Int) {
    AnimatedContent(seconds, modifier = Modifier.align(Alignment.Center).testTag("camera-countdown"),
        transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "camera-countdown") { remaining ->
        if (remaining > 0) Box(Modifier.size(88.dp).background(Color.Black.copy(alpha = .38f), CircleShape),
            contentAlignment = Alignment.Center) { Text(remaining.toString(), color = Color.White, fontSize = 44.sp) }
        else Spacer(Modifier.size(0.dp))
    }
}
