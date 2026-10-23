package com.kigtts.next.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import com.kigtts.plugin.api.*

/** Only two fingers move the photograph; single-finger taps and parent scrolling stay available. */
@Composable
internal fun CardPhotoBackground(card: PolaroidCard, image: ImageBitmap?, enabled: Boolean,
    change: (PolaroidCard) -> Unit, finish: () -> Unit, modifier: Modifier = Modifier) {
    val latest = rememberUpdatedState(card); val update = rememberUpdatedState(change); val end = rememberUpdatedState(finish)
    val feedback = rememberUpdatedState(LocalHapticFeedback.current)
    BoxWithConstraints(modifier.background(Color(0xFFB0B0B0)).testTag("card-photo/" + card.id), contentAlignment = Alignment.Center) {
        val width = constraints.maxWidth.toFloat(); val height = constraints.maxHeight.toFloat()
        CardPhotoContent(card, image, width, height)
        if (enabled && image != null) Box(Modifier.matchParentSize().pointerInput(card.id, enabled) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var began = false; var changed = false; var completed = false
                var x = latest.value.photoX; var y = latest.value.photoY; var zoom = latest.value.photoZoom
                try {
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } >= 2 && event.changes.none { it.isConsumed }) {
                            if (!began) { began = true; feedback.value.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) }
                            val pan = event.calculatePan(); val factor = event.calculateZoom()
                            x = (x + pan.x / width).coerceIn(0.0, 1.0); y = (y + pan.y / height).coerceIn(0.0, 1.0)
                            zoom = (zoom * factor).coerceIn(1.0, 8.0)
                            if (pan.x != 0f || pan.y != 0f || factor != 1f) {
                                changed = true; update.value(latest.value.copy(photoX = x, photoY = y, photoZoom = zoom))
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    completed = true
                } finally {
                    if (completed && changed) { feedback.value.performHapticFeedback(HapticFeedbackType.GestureEnd); end.value() }
                }
            }
        })
    }
}
