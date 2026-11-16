package com.kigtts.next.theme

import android.graphics.Typeface
import android.util.LruCache
import androidx.compose.ui.text.font.*
import java.io.File

private val fontCache = LruCache<String, AppearanceFont>(16)

/** Use the same font file, weight mapping and height correction on Compose and Canvas. */
internal class AppearanceTypeface(private val file: File, private val weight: Int) {
    val axis = readFontWeightAxis(file)
    private val base by lazy { face(axis?.clamp(weight)?.toFloat()) }
    val heightScale by lazy { AppearanceAssets.heightScale(base) }

    fun forWeight(style: Int): Typeface = if (style == 400 || axis == null) base else face(axis.forStyle(weight, style))

    private fun face(weight: Float?): Typeface = if (weight == null) Typeface.createFromFile(file) else
        Typeface.Builder(file).setFontVariationSettings("'wght' $weight").build() ?: error("无法读取字体")
}

/** Construct off the UI thread; Compose preloads the family before it becomes visible. */
internal fun loadAppearanceFont(file: File, weight: Int): AppearanceFont {
    val key = "${file.path}:${file.lastModified()}:$weight"
    fontCache.get(key)?.let { return it }
    val font = AppearanceTypeface(file, weight)
    val axis = font.axis
    val family = if (axis == null) FontFamily(font.forWeight(400)) else FontFamily((100..900 step 100).map { style ->
        Font(file, weight = FontWeight(style), variationSettings = FontVariation.Settings(
            FontVariation.Setting("wght", axis.forStyle(weight, style))))
    })
    return AppearanceFont(family, font.heightScale).also { fontCache.put(key, it) }
}
