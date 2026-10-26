package com.kigtts.next.identity

import android.graphics.*
import androidx.core.graphics.ColorUtils



internal fun identityInk(pixels: Bitmap?): Int {
    if (pixels == null) return Color.WHITE
    var luminance = 0.0; var count = 0
    for (y in 7..9) for (x in 2..7) {
        luminance += ColorUtils.calculateLuminance(pixels.getPixel(pixels.width * x / 10, pixels.height * y / 10)); count++
    }
    return if (luminance / count > .42) 0xFF202020.toInt() else Color.WHITE
}
