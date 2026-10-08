package com.kigtts.next.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/** Convert the window-relative em width to native sp, including Android font scaling. */
@Composable
fun preferredSubtitleFontSize(percent: Float): TextUnit {
    val density = LocalDensity.current
    val windowWidth = LocalWindowInfo.current.containerSize.width.toFloat()
    val fallbackWidth = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    return with(density) { (windowWidth.takeIf { it > 0f } ?: fallbackWidth).times(percent / 100f).toSp() }
}
