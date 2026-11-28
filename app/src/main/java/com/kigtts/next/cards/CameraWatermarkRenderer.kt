package com.kigtts.next.cards

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.exifinterface.media.ExifInterface
import com.kigtts.next.R
import com.kigtts.plugin.api.PolaroidGeometry
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

internal data class WatermarkFacts(val date: String = "", val time: String = "", val exposure: String = "",
    val location: String = "", val weather: String = "")

internal fun watermarkFacts(source: File?, taken: FrozenPhotoFacts): WatermarkFacts {
    val exif = source?.let { runCatching { ExifInterface(it) }.getOrNull() }
    fun number(key: String, suffix: String, prefix: String = "") = exif?.getAttributeDouble(key, 0.0)?.takeIf { it.isFinite() && it > 0 }
        ?.let { "$prefix" + String.format(Locale.ROOT, "%.1f", it).removeSuffix(".0") + suffix }
    val exposure = listOfNotNull(number(ExifInterface.TAG_FOCAL_LENGTH, "mm"),
        number(ExifInterface.TAG_F_NUMBER, "", "ƒ/"),
        exif?.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)?.takeIf { it in 0.000001..120.0 }?.let {
            if (it < 1) "1/${(1 / it).roundToInt()}s" else "${it}s"
        }, exif?.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0)?.takeIf { it > 0 }?.let { "ISO $it" })
        .joinToString("  ·  ")
    return WatermarkFacts(taken.capturedAt?.let { taken.text(PhotoTextField.DATE) }.orEmpty(),
        taken.capturedAt?.let { taken.text(PhotoTextField.TIME) }.orEmpty(), exposure,
        taken.location?.let(::cleanWatermarkFact).orEmpty(),
        taken.weather?.let(::photoWeatherCaption)?.let(::cleanWatermarkFact).orEmpty())
}

internal fun cleanWatermarkFact(value: String): String =
    value.asSequence().take(240).map { if (it.isISOControl() || it == '\u2028' || it == '\u2029') ' ' else it }
        .joinToString("").trim()

internal fun watermarkRequestedFields(style: CameraWatermarkStyle): Set<PhotoTextField> = buildSet {
    if (style.showLocation) add(PhotoTextField.LOCATION)
    if (style.showWeather) add(PhotoTextField.WEATHER)
}

internal data class WatermarkLine(val text: String, val weight: Float = 1f)

internal fun watermarkLines(style: CameraWatermarkStyle, facts: WatermarkFacts): List<WatermarkLine> = buildList {
    val time = facts.time.takeIf { style.showTime }.orEmpty()
    val date = facts.date.takeIf { style.showDate }.orEmpty()
    val place = facts.location.ifBlank { "地点未知" }.takeIf { style.showLocation }
    val weather = facts.weather.ifBlank { "天气未知" }.takeIf { style.showWeather }
    val information = listOfNotNull(place, weather).joinToString("  ·  ")
    if (style.layout == WatermarkLayout.CHECK_IN && time.isNotBlank()) {
        add(WatermarkLine(time, 1.42f))
        if (date.isNotBlank()) add(WatermarkLine(date, .8f))
    } else {
        if (style.showExposure && facts.exposure.isNotBlank()) add(WatermarkLine(facts.exposure))
        val dateTime = listOf(date, time).filter(String::isNotBlank).joinToString("  ")
        if (dateTime.isNotBlank()) add(WatermarkLine(dateTime, if (isEmpty()) 1f else .8f))
    }
    if (information.isNotBlank()) add(WatermarkLine(information, if (isEmpty()) 1f else .83f))
}

/** Same proportional drawing for the selector, image editor, and still/motion output. */
internal fun drawCameraWatermark(context: Context, canvas: Canvas, style: CameraWatermarkStyle,
    geometry: PolaroidGeometry, facts: WatermarkFacts, width: Float, height: Float, glass: PhotoFrameGlass? = null) {
    val unit = width / geometry.paperWidth.toFloat()
    val top = style.position == WatermarkPosition.TOP && !style.overlay
    val bandHeight = minOf(geometry.photoWidth, geometry.photoHeight).toFloat() * unit * style.band / 100
    if (width <= 0f || bandHeight <= 0f) return
    val band = RectF(0f, if (top) 0f else height - bandHeight, width, if (top) bandHeight else height)
    if (style.palette == WatermarkPalette.GLASS) {
        glass?.draw(canvas, RectF(0f, 0f, width, height), RectF(
            (geometry.photoLeft * unit).toFloat(), (geometry.photoTop * unit).toFloat(),
            (geometry.photoRight * unit).toFloat(), (geometry.photoBottom * unit).toFloat()))
    }
    val ink = if (style.overlay || style.palette == WatermarkPalette.GLASS ||
        Color.luminance(style.paperColor) < .42) Color.WHITE else 0xFF182124.toInt()
    val margin = width * .042f
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    fun text(value: String, x: Float, y: Float, maximum: Float, fontSize: Float, center: Boolean = false, right: Boolean = false) {
        if (value.isBlank() || maximum <= 0f || fontSize <= 0f) return
        paint.textSize = fontSize
        if (style.overlay) paint.setShadowLayer((fontSize * .15f).coerceAtLeast(1f), 0f, fontSize * .075f, 0xD0000000.toInt())
        paint.textAlign = if (center) Paint.Align.CENTER else if (right) Paint.Align.RIGHT else Paint.Align.LEFT
        val line = TextUtils.ellipsize(value, paint, maximum, TextUtils.TruncateAt.END)
        canvas.drawText(line.toString(), x, y - (paint.ascent() + paint.descent()) / 2, paint)
    }
    val lines = watermarkLines(style, facts)
    val centered = style.center && !style.overlay
    val centeredLogo = style.showLogo && centered && lines.isNotEmpty()
    val topText = band.top + bandHeight * if (centeredLogo) .34f else .10f
    val heightText = band.bottom - bandHeight * .10f - topText
    val units = lines.sumOf { it.weight.toDouble() }.toFloat()
    val font = minOf(width * .037f * style.scale / 100, heightText / (units.coerceAtLeast(1f) * 1.31f))
    var logoWidth = 0f
    if (style.showLogo) {
        val logo = context.getDrawable(R.drawable.logo_kigtts_wordmark)?.mutate()
        val ratio = logo?.let { it.intrinsicWidth.toFloat() / it.intrinsicHeight.coerceAtLeast(1) }?.coerceIn(1f, 16f) ?: 4f
        val logoHeight = minOf(watermarkLogoHeight(style.layout, centeredLogo, width, bandHeight, ratio),
            if (lines.isNotEmpty()) font * .95f else Float.POSITIVE_INFINITY)
        val fittedWidth = logoHeight * ratio
        if (logo != null) logoWidth = fittedWidth
        val x = if (style.overlay) width - margin - fittedWidth else if (centered) (width - fittedWidth) / 2 else margin
        val y = when {
            style.overlay -> height - margin * .72f - logoHeight / 2
            centeredLogo -> band.top + bandHeight * .19f
            else -> band.centerY()
        }
        logo?.apply {
            if (style.overlay) {
                setTint(0xCC000000.toInt())
                val offset = (logoHeight * .08f).roundToInt().coerceAtLeast(1)
                setBounds(x.roundToInt(), (y - logoHeight / 2).roundToInt() + offset,
                    (x + fittedWidth).roundToInt(), (y + logoHeight / 2).roundToInt() + offset)
                draw(canvas)
            }
            setTint(ink)
            setBounds(x.roundToInt(), (y - logoHeight / 2).roundToInt(), (x + fittedWidth).roundToInt(), (y + logoHeight / 2).roundToInt())
            draw(canvas)
        }
    }
    val left = margin + if (style.showLogo && !centered && !style.overlay) logoWidth + margin else 0f
    val right = width - margin - if (style.overlay && logoWidth > 0f) logoWidth + margin else 0f
    var y = if (style.overlay) height - margin * .72f - font * 1.31f * units
        else topText + (heightText - font * 1.31f * units) * .5f
    for (line in lines) {
        val depth = font * 1.31f * line.weight
        val alignRight = !centered && !style.overlay && style.layout != WatermarkLayout.CHECK_IN
        text(line.text, if (centered) width / 2 else if (alignRight) right else left,
            y + depth / 2, right - left, font * line.weight, centered, alignRight)
        y += depth
    }
}


internal fun watermarkLogoHeight(layout: WatermarkLayout, centeredWithText: Boolean, width: Float, bandHeight: Float, ratio: Float): Float {
    val prominent = layout == WatermarkLayout.STANDARD && !centeredWithText
    val widthLimit = minOf(width * if (prominent) .30f else .25f, bandHeight * if (centeredWithText) .9f else 1.65f)
    return minOf(widthLimit / ratio, bandHeight * if (prominent) .30f else .22f)
}
