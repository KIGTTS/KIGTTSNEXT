package com.kigtts.next.theme

import android.graphics.Typeface
import android.util.LruCache
import androidx.compose.ui.text.font.*
import java.io.File

private val fontCache = LruCache<String, AppearanceFont>(16)

/** Construct off the UI thread; Compose preloads the family before it becomes visible. */
internal fun loadAppearanceFont(file: File, weight: Int): AppearanceFont {
    val key = "${file.path}:${file.lastModified()}:$weight"
    fontCache.get(key)?.let { return it }
    val axis = readFontWeightAxis(file)
    val face = if (axis == null) Typeface.createFromFile(file) else Typeface.Builder(file)
        .setFontVariationSettings("'wght' ${axis.clamp(weight)}").build() ?: error("无法读取字体")
    val family = if (axis == null) FontFamily(face) else FontFamily((100..900 step 100).map { style ->
        Font(file, weight = FontWeight(style), variationSettings = FontVariation.Settings(
            FontVariation.Setting("wght", axis.forStyle(weight, style))))
    })
    return AppearanceFont(family, AppearanceAssets.heightScale(face)).also { fontCache.put(key, it) }
}
