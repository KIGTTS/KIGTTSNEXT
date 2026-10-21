package com.kigtts.next.ui.cards.camera

import com.kigtts.plugin.api.PolaroidFormat
import java.io.Closeable
import java.io.File

enum class PhotoFilter(val label: String) {
    ORIGINAL("原画"), BW("黑白"), WARM("暖色"), COOL("冷色"), VINTAGE("复古"),
    FILM_SOFT("柔彩胶片"), FILM_VIVID("鲜明胶片"), FILM_FADE("淡彩胶片"), CUSTOM("自定义 LUT"),
    BLACK_GOLD("黑金"), INK("写意"), MONSOON("季风"), KYOTO("京都"), OLD_ALLEY("旧巷")
}

/** Import both files before closing; the JPEG returned by CameraX is never edited in place. */
class PolaroidCameraCapture internal constructor(val original: File, val processed: File?,
    val format: PolaroidFormat, val filter: PhotoFilter, val motion: CameraMotionClip? = null) : Closeable {
    val displayFile: File get() = processed ?: original
    internal fun withMotion(clip: CameraMotionClip?) = PolaroidCameraCapture(original, processed, format, filter, clip)
    fun discard() { motion?.video?.delete(); processed?.takeIf { it != original }?.delete(); original.delete() }
    override fun close() = discard()
}

internal fun cameraFormats(width: Int, height: Int, smallestWidth: Int): List<PolaroidFormat> {
    val nearSquare = minOf(width, height).toDouble() / maxOf(width, height).coerceAtLeast(1) >= .8
    return if (smallestWidth >= 600 || nearSquare) listOf(PolaroidFormat.MINI, PolaroidFormat.WIDE, PolaroidFormat.SQUARE)
    else listOf(if (width > height) PolaroidFormat.WIDE else PolaroidFormat.MINI, PolaroidFormat.SQUARE)
}

internal fun PolaroidFormat.cameraLabel(expanded: Boolean) = when (this) {
    PolaroidFormat.MINI -> if (expanded) "竖版" else "矩形"
    PolaroidFormat.WIDE -> if (expanded) "横版" else "矩形"
    PolaroidFormat.SQUARE -> "方形"
    PolaroidFormat.LEGACY -> "经典"
}
