package com.kigtts.next.state

import android.content.Context
import androidx.core.content.edit

/** Display preferences are independent of the text and its per-range formatting. */
data class SubtitleAppearance(
    val bold: Boolean = false,
    val alignment: SubtitleAlignment = SubtitleAlignment.CENTER,
    val richTextEditing: Boolean = false,
    val reducedMotion: Boolean = false,
    val displayReflow: Boolean = true,
)

enum class SubtitleAlignment(val label: String, val row: Int, val column: Int) {
    TOP_LEFT("左上", 0, 0), TOP_CENTER("上中", 0, 1), TOP_RIGHT("右上", 0, 2),
    CENTER_LEFT("左中", 1, 0), CENTER("居中", 1, 1), CENTER_RIGHT("右中", 1, 2),
    BOTTOM_LEFT("左下", 2, 0), BOTTOM_CENTER("下中", 2, 1), BOTTOM_RIGHT("右下", 2, 2),
}

internal class SubtitleAppearanceStore(context: Context) {
    private val preferences = context.getSharedPreferences("subtitle-appearance", Context.MODE_PRIVATE)
    fun load() = SubtitleAppearance(preferences.getBoolean("bold", false),
        runCatching { SubtitleAlignment.valueOf(preferences.getString("alignment", null).orEmpty()) }.getOrDefault(SubtitleAlignment.CENTER),
        preferences.getBoolean("rich-text-editing", false),
        preferences.getBoolean("reduced-motion", false), preferences.getBoolean("display-reflow", true))
    fun save(value: SubtitleAppearance) { preferences.edit {
        putBoolean("bold", value.bold); putString("alignment", value.alignment.name)
        putBoolean("rich-text-editing", value.richTextEditing)
        putBoolean("reduced-motion", value.reducedMotion); putBoolean("display-reflow", value.displayReflow)
    } }
}
