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
    geometry: PolaroidGeometry, facts: WatermarkFacts, width: Float, height: Float) {
    val unit = width / geometry.paperWidth.toFloat()
    val top = style.position == WatermarkPosition.TOP
    val bandHeight = minOf(geometry.photoWidth, geometry.photoHeight).toFloat() * unit * style.band / 100
    if (width <= 0f || bandHeight <= 0f) return
    val band = RectF(0f, if (top) 0f else height - bandHeight, width, if (top) bandHeight else height)
    val ink = if (Color.luminance(style.palette.color) < .42) Color.WHITE else 0xFF182124.toInt()
    val margin = width * .042f
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    fun text(value: String, x: Float, y: Float, maximum: Float, fontSize: Float, center: Boolean = false, right: Boolean = false) {
        if (value.isBlank() || maximum <= 0f || fontSize <= 0f) return
        paint.textSize = fontSize
        paint.textAlign = if (center) Paint.Align.CENTER else if (right) Paint.Align.RIGHT else Paint.Align.LEFT
        val line = TextUtils.ellipsize(value, paint, maximum, TextUtils.TruncateAt.END)
        canvas.drawText(line.toString(), x, y - (paint.ascent() + paint.descent()) / 2, paint)
    }
    val lines = watermarkLines(style, facts)
    val centeredLogo = style.showLogo && style.center && lines.isNotEmpty()
    val logoWidth = minOf(width * .25f, bandHeight * if (centeredLogo) .9f else 1.65f)
    if (style.showLogo) {
        val logo = context.getDrawable(R.drawable.logo_horizontal)?.mutate()
        val ratio = logo?.let { it.intrinsicWidth.toFloat() / it.intrinsicHeight.coerceAtLeast(1) }?.coerceIn(1f, 16f) ?: 4f
        val logoHeight = minOf(logoWidth / ratio, bandHeight * .22f)
        val fittedWidth = logoHeight * ratio
        val x = if (style.center) (width - fittedWidth) / 2 else margin
        val y = if (centeredLogo) band.top + bandHeight * .19f else band.centerY()
        logo?.apply {
            setTint(ink)
            setBounds(x.roundToInt(), (y - logoHeight / 2).roundToInt(), (x + fittedWidth).roundToInt(), (y + logoHeight / 2).roundToInt())
            draw(canvas)
        }
    }
    val left = margin + if (style.showLogo && !style.center) logoWidth + margin else 0f
    val right = width - margin
    val topText = band.top + bandHeight * if (centeredLogo) .34f else .10f
    val heightText = band.bottom - bandHeight * .10f - topText
    val units = lines.sumOf { it.weight.toDouble() }.toFloat()
    val font = minOf(width * .037f * style.scale / 100, heightText / (units.coerceAtLeast(1f) * 1.31f))
    var y = topText + (heightText - font * 1.31f * units) * .5f
    for (line in lines) {
        val depth = font * 1.31f * line.weight
        val alignRight = !style.center && style.layout != WatermarkLayout.CHECK_IN
        text(line.text, if (style.center) width / 2 else if (alignRight) right else left,
            y + depth / 2, right - left, font * line.weight, style.center, alignRight)
        y += depth
    }
}
