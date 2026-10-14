package com.kigtts.next.ui.cards

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import kotlin.math.roundToInt

/** Keep the floating app bar beside the controls without reserving height for the whole page. */
internal fun Modifier.cardToolbarSpace(chrome: CardPageChromeState): Modifier = layout { child, constraints ->
    val inset = chrome.panelEndInset?.value?.invoke()?.let { it + 8.dp.toPx() }
        ?: if (chrome.albums == null) 0f
        else (listOf(54.dp, 144.dp, 264.dp)[chrome.albumStage?.value ?: 0] + 16.dp).toPx()
    val reserved = inset.roundToInt().coerceIn(0, constraints.maxWidth)
    val measured = child.measure(constraints.offset(horizontal = -reserved))
    layout(measured.width + reserved, measured.height) { measured.placeRelative(0, 0) }
}
