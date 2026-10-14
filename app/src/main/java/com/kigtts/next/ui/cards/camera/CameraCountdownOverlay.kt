package com.kigtts.next.ui.cards.camera

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun BoxScope.CameraCountdownOverlay(seconds: Int) {
    var lastVisibleSeconds by remember { mutableIntStateOf(seconds.coerceAtLeast(1)) }
    SideEffect { if (seconds > 0) lastVisibleSeconds = seconds }
    val remaining = if (seconds > 0) seconds else lastVisibleSeconds
    CameraOverlayPresence(seconds > 0, Modifier.align(Alignment.Center).size(88.dp).testTag("camera-countdown")) {
        // Keep the backing and last digit intact until exit completes, including cancellation.
        Box(Modifier.size(88.dp).background(Color.Black.copy(alpha = .38f), CircleShape)) {
            AnimatedContent(remaining, modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center,
                transitionSpec = { (fadeIn(tween(160)) togetherWith fadeOut(tween(120))).using(null) },
                label = "camera-countdown-digit") { digit ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(digit.toString(), color = Color.White, fontSize = 44.sp)
                }
            }
        }
    }
}
