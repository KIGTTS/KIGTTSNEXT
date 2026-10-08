package com.kigtts.next.state

import android.content.Context
import androidx.core.content.edit

object SubtitleSize {
    const val DEFAULT_PERCENT = 3800f / 393f
    val range = 3f..25f
    fun normalize(percent: Float) = if (percent.isFinite()) percent.coerceIn(range) else DEFAULT_PERCENT
}

/** Store the preferred CJK em width as a percentage, without screen size or density. */
class SubtitleSizeStore(context: Context) {
    private val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    fun load() = SubtitleSize.normalize(preferences.getFloat("subtitleWidthPercent", SubtitleSize.DEFAULT_PERCENT))
    fun save(percent: Float) { preferences.edit { putFloat("subtitleWidthPercent", SubtitleSize.normalize(percent)) } }
}
