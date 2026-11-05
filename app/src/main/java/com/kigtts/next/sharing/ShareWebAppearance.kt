package com.kigtts.next.sharing

import android.content.Context
import android.graphics.Typeface
import com.kigtts.next.media.WebThemeState
import com.kigtts.next.theme.*
import org.json.JSONObject
import java.io.File

/** Appearance assets travel only with the user's device-to-device share session. */
internal fun shareWebAppearance(context: Context, directory: File, files: MutableList<File>): JSONObject {
    val settings = ThemeSettingsStore(context).load()
    val appearance = JSONObject().put("theme", faceWebTheme(WebThemeState.current).json()).put("fontScale", 1)
        .put("tileCorner", (settings.cornerRadius + 8).coerceAtMost(24f))
    val assets = AppearanceAssets(context)
    assets.file(settings.font)?.takeIf { it.extension in setOf("ttf", "otf") }?.let { font ->
        val axis = readFontWeightAxis(font)
        val face = if (axis == null) Typeface.createFromFile(font) else Typeface.Builder(font)
            .setFontVariationSettings("'wght' ${axis.clamp(settings.fontWeight)}").build()
        val copy = File(directory, "profile-font.${font.extension}")
        font.copyTo(copy, overwrite = true); files += copy
        appearance.put("font", copy.name).put("fontScale", AppearanceAssets.heightScale(face))
            .put("weights", JSONObject((100..900 step 100).associate { style ->
                style.toString() to (axis?.forStyle(settings.fontWeight, style) ?: 400f)
            }))
        if (axis != null) appearance.put("weightMin", axis.min).put("weightMax", axis.max)
    }
    if (settings.secondaryPageBackground) BackgroundBlurCache(context).file(settings.background, BackgroundBlur.PAGE)?.let { source ->
        val copy = File(directory, "share-background.png")
        source.copyTo(copy, overwrite = true); files += copy
        appearance.put("background", copy.name)
            .put("backgroundDim", appearanceBackgroundDim(settings.backgroundDim, BackgroundBlur.PAGE))
    }
    return appearance
}

internal fun webAppearanceAsset(name: String) = name == "share-background.png" || name in setOf("profile-font.ttf", "profile-font.otf")
