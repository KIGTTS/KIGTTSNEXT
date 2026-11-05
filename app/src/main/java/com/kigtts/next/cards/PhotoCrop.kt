package com.kigtts.next.cards

import kotlin.math.roundToInt

/** Upright source coordinates. The source itself remains unchanged. */
internal data class PhotoCrop(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val valid get() = left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f &&
        width >= .02f && height >= .02f

    fun pixels(sourceWidth: Int, sourceHeight: Int): IntArray {
        val x = (left * sourceWidth).roundToInt().coerceIn(0, sourceWidth - 1)
        val y = (top * sourceHeight).roundToInt().coerceIn(0, sourceHeight - 1)
        return intArrayOf(x, y, (right * sourceWidth).roundToInt().coerceIn(x + 1, sourceWidth),
            (bottom * sourceHeight).roundToInt().coerceIn(y + 1, sourceHeight))
    }

    fun encode() = listOf(left, top, right, bottom).joinToString("") {
        (it * PRECISION).roundToInt().toString(36).padStart(3, '0')
    }

    companion object {
        val Full = PhotoCrop(0f, 0f, 1f, 1f)
        private const val PRECISION = 46655f

        fun decode(text: String): PhotoCrop? {
            if (text.length != 12) return null
            val edges = text.chunked(3).map { (it.toIntOrNull(36) ?: return null) / PRECISION }
            return PhotoCrop(edges[0], edges[1], edges[2], edges[3]).takeIf { it.valid }
        }
    }
}
