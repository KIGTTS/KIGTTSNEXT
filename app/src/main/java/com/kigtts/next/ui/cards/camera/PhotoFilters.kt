package com.kigtts.next.ui.cards.camera

import android.graphics.ColorMatrix
import kotlin.math.roundToInt

/** Shared encoded-RGB coefficients for the CameraX GPU preview and full-resolution saved photograph. */
internal object PhotoFilters {
    fun coefficients(filter: PhotoFilter, strength: Float = 1f): FloatArray {
        val selected = preset(filter)
        val weight = strength.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 1f
        if (weight == 1f || filter == PhotoFilter.ORIGINAL) return selected
        val original = preset(PhotoFilter.ORIGINAL)
        return FloatArray(20) { original[it] + (selected[it] - original[it]) * weight }
    }
    private fun preset(filter: PhotoFilter): FloatArray = when (filter) {
        PhotoFilter.ORIGINAL, PhotoFilter.CUSTOM -> floatArrayOf(1f,0f,0f,0f,0f, 0f,1f,0f,0f,0f, 0f,0f,1f,0f,0f, 0f,0f,0f,1f,0f)
        PhotoFilter.BW -> floatArrayOf(.213f,.715f,.072f,0f,0f, .213f,.715f,.072f,0f,0f, .213f,.715f,.072f,0f,0f, 0f,0f,0f,1f,0f)
        PhotoFilter.WARM -> floatArrayOf(1.04f,0f,0f,0f,5f, 0f,1f,0f,0f,1f, 0f,0f,.93f,0f,0f, 0f,0f,0f,1f,0f)
        PhotoFilter.COOL -> floatArrayOf(.94f,0f,0f,0f,0f, 0f,1f,0f,0f,1f, 0f,0f,1.05f,0f,4f, 0f,0f,0f,1f,0f)
        PhotoFilter.VINTAGE -> floatArrayOf(.90f,.10f,.03f,0f,9f, .06f,.89f,.03f,0f,4f, .03f,.10f,.77f,0f,4f, 0f,0f,0f,1f,0f)
        // Original encoded-RGB film looks, not Fujifilm's F-Log camera transforms.
        PhotoFilter.FILM_SOFT -> floatArrayOf(.91f,.06f,.03f,0f,8f, .02f,.93f,.03f,0f,6f, .04f,.05f,.86f,0f,8f, 0f,0f,0f,1f,0f)
        PhotoFilter.FILM_VIVID -> floatArrayOf(1.18f,-.06f,-.02f,0f,-8f, -.04f,1.14f,-.02f,0f,-5f, -.02f,-.06f,1.16f,0f,-6f, 0f,0f,0f,1f,0f)
        PhotoFilter.FILM_FADE -> floatArrayOf(.86f,.08f,.04f,0f,14f, .04f,.86f,.05f,0f,10f, .05f,.07f,.81f,0f,14f, 0f,0f,0f,1f,0f)
    }
    fun matrix(filter: PhotoFilter, strength: Float = 1f) = ColorMatrix(coefficients(filter, strength))
    fun apply(color: Int, values: FloatArray): Int {
        val red = color ushr 16 and 255; val green = color ushr 8 and 255; val blue = color and 255
        fun channel(offset: Int) = (values[offset] * red + values[offset + 1] * green + values[offset + 2] * blue + values[offset + 4])
            .roundToInt().coerceIn(0, 255)
        return (color and -0x1000000) or (channel(0) shl 16) or (channel(5) shl 8) or channel(10)
    }
}
