package com.kigtts.next.identity

import android.graphics.Color
import androidx.core.graphics.ColorUtils

internal data class ProfileQrPalette(val ink: Int, val paper: Int)

/** QR marks and their tile are a single palette, including the quiet zone. */
internal fun profileQrPalette(style: ProfileQrStyle, keepLight: Boolean, dark: Boolean, primary: Int, tile: Int): ProfileQrPalette {
    val inverse = dark && !keepLight
    if (style == ProfileQrStyle.INK) return if (inverse) ProfileQrPalette(Color.WHITE, Color.BLACK)
        else ProfileQrPalette(Color.BLACK, Color.WHITE)
    val paper = if (dark && keepLight) ColorUtils.blendARGB(primary, Color.WHITE, .91f) else tile
    val ink = ColorUtils.blendARGB(primary, if (inverse) Color.WHITE else Color.BLACK, if (inverse) .58f else .65f)
    val monochrome = if (inverse) Color.WHITE else Color.BLACK
    return ProfileQrPalette(if (ColorUtils.calculateContrast(ink, paper) >= 4.5) ink else monochrome, paper)
}
