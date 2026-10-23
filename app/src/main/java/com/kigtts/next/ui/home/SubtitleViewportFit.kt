package com.kigtts.next.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle

/** Fit follows the live viewport; drawing animations never defer a larger font. */
@Composable
internal fun stableSubtitleFontSize(text: AnnotatedString, style: TextStyle, width: Int, height: Int): Float {
    return fittingSubtitleFontSize(text, style, width, height)
}
