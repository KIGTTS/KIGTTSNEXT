package com.kigtts.next.ui.cards.camera

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.kigtts.next.cards.CameraWatermarkAsset
import com.kigtts.next.cards.WatermarkPalette
import com.kigtts.next.cards.WatermarkPosition
import com.kigtts.plugin.api.PolaroidFormat
import com.kigtts.next.i18n.LocalUiLocale
import com.kigtts.next.i18n.uiCopy
import com.kigtts.next.media.color.PhotoEffect
import java.util.Locale

@Composable
internal fun rememberCameraCopy(): CameraCopy {
    val context = LocalContext.current
    val locale = LocalUiLocale.current
    return remember(context, locale) { CameraCopy(context, locale) }
}

internal class CameraCopy(private val context: Context, private val locale: String? = null) {
    fun text(key: String, vararg values: Pair<String, String>): String =
        if (locale == null) context.uiCopy(key, *values) else context.uiCopy(key, *values, locale = locale)

    fun toggle(on: Boolean) = text(if (on) "option.on" else "option.off")
    fun shutter(nanos: Long) = text("camera.timer_seconds", "seconds" to shutterDisplayValue(nanos))
    private fun option(section: String, value: Enum<*>) = text("camera.$section.${value.name.lowercase(Locale.ROOT)}")
    fun label(value: CameraMode) = option("mode", value)
    fun label(value: CameraAspect) = if (value == CameraAspect.FULL) text("camera.aspect.full") else value.label
    fun label(value: CameraFlash) = when (value) {
        CameraFlash.AUTO -> text("option.auto")
        else -> toggle(value == CameraFlash.ON)
    }
    fun label(value: CameraGrid) = if (value == CameraGrid.OFF) toggle(false) else option("grid", value)
    fun label(value: CameraProcessing) = if (value == CameraProcessing.HDR) "HDR" else option("processing", value)
    fun label(value: CameraWhiteBalance) = if (value.name == "AUTO") text("option.auto") else option("wb", value)
    fun label(value: ProControl) = option("pro", value)
    fun label(value: PhotoEffect) = option("effect", value)
    fun label(value: PhotoFilter) = option("filter", value)
    fun label(value: WatermarkPalette) = option("palette", value)
    fun label(value: WatermarkPosition) = option("watermark.position", value)
    fun label(asset: CameraWatermarkAsset) = when (asset.id) {
        "standard", "check-in", "classic" -> text("camera.watermark.${asset.id}")
        else -> asset.name
    }
    fun aspect(value: PolaroidFormat, expanded: Boolean) = when {
        !expanded && (value == PolaroidFormat.MINI || value == PolaroidFormat.WIDE) -> text("camera.paper.rectangle")
        else -> option("paper", value)
    }
}
