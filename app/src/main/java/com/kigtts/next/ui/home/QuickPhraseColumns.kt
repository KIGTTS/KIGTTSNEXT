package com.kigtts.next.ui.home

/** Keep phone-sized cards familiar; extra tablet width adds columns in either orientation. */
internal fun quickPhraseColumns(widthDp: Float, minimum: Int = 2): Int =
    ((widthDp + 4f) / 168f).toInt().coerceIn(minimum, 6)
