package com.kigtts.next.audio

import android.content.Context

internal class CaptionFontSettings(context: Context) {
    private val preferences = context.getSharedPreferences("caption-window", Context.MODE_PRIVATE)
    fun panel() = preferences.getFloat("panel-font-size", PANEL_DEFAULT).bounded(PANEL_DEFAULT, PANEL_RANGE)
    fun window() = preferences.getFloat("font-size", WINDOW_DEFAULT).bounded(WINDOW_DEFAULT, WINDOW_RANGE)
    fun savePanel(size: Float) = size.bounded(PANEL_DEFAULT, PANEL_RANGE).also {
        if (it != panel()) preferences.edit().putFloat("panel-font-size", it).apply()
    }
    fun saveWindow(size: Float) {
        val bounded = size.bounded(WINDOW_DEFAULT, WINDOW_RANGE)
        if (bounded != window()) preferences.edit().putFloat("font-size", bounded).apply()
    }

    companion object {
        const val PANEL_DEFAULT = 20f
        const val WINDOW_DEFAULT = 22f
        val PANEL_RANGE = 10f..64f
        val WINDOW_RANGE = 12f..44f
        private fun Float.bounded(defaultValue: Float, range: ClosedFloatingPointRange<Float>) =
            if (isFinite()) coerceIn(range) else defaultValue
    }
}
