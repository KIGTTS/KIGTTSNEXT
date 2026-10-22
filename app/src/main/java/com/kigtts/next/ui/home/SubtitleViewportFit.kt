package com.kigtts.next.ui.home

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay

/** Shrink to fit immediately; wait for the viewport to settle before growing again. */
@Composable
internal fun stableSubtitleFontSize(text: AnnotatedString, style: TextStyle, width: Int, height: Int): Float {
    val candidate = fittingSubtitleFontSize(text, style, width, height)
    val metricsStyle = style.copy(color = Color.Unspecified)
    var accepted by remember(text, metricsStyle) { mutableFloatStateOf(candidate) }
    LaunchedEffect(width, height, candidate, text, metricsStyle) {
        if (candidate < accepted) accepted = candidate
        // Spatial springs can cross a line-fit threshold several times while settling.
        // Do not alternate between adjacent font sizes on every rebound frame.
        delay(120)
        accepted = candidate
    }
    return minOf(accepted, candidate)
}
