package com.kigtts.next.ui.home

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

internal val LocalSubtitleMagnifierEnabled = staticCompositionLocalOf { true }

/** Called by the small, signature-checked Foundation build bridge. Native handles,
 * selection, copy/paste and the IME are unaffected; only this field's lens is omitted. */
fun withSubtitleMagnifierPolicy(magnifier: Modifier, base: Modifier): Modifier = Modifier.composed {
    if (LocalSubtitleMagnifierEnabled.current) magnifier else base
}
