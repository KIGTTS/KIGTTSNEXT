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

internal data class WatermarkFacts(val date: String = "", val time: String = "", val exposure: String = "")

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
        taken.capturedAt?.let { taken.text(PhotoTextField.TIME) }.orEmpty(), exposure)
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
    val size = minOf(width * .036f, bandHeight * .34f) * style.scale / 100
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    fun text(value: String, x: Float, y: Float, maximum: Float, fontSize: Float, center: Boolean = false, right: Boolean = false) {
        if (value.isBlank() || maximum <= 0f || fontSize <= 0f) return
        paint.textSize = fontSize
        paint.textAlign = if (center) Paint.Align.CENTER else if (right) Paint.Align.RIGHT else Paint.Align.LEFT
        val line = TextUtils.ellipsize(value, paint, maximum, TextUtils.TruncateAt.END)
        canvas.drawText(line.toString(), x, y - (paint.ascent() + paint.descent()) / 2, paint)
    }
    val center = band.centerY()
    if (style.showLogo) {
        val logoWidth = minOf(width * .25f, bandHeight * 1.65f)
        val logoHeight = logoWidth / 4f
        val x = if (style.center) (width - logoWidth) / 2 else margin
        val y = if (style.center) center - bandHeight * .21f else center
        context.getDrawable(R.drawable.logo_horizontal)?.mutate()?.apply {
            setTint(ink)
            setBounds(x.roundToInt(), (y - logoHeight / 2).roundToInt(), (x + logoWidth).roundToInt(), (y + logoHeight / 2).roundToInt())
            draw(canvas)
        }
    }
    val dateTime = listOfNotNull(facts.date.takeIf { style.showDate }, facts.time.takeIf { style.showTime }).filter(String::isNotBlank).joinToString("  ")
    val camera = facts.exposure.takeIf { style.showExposure }.orEmpty()
    val x = if (style.center) width / 2 else width - margin
    val maximum = if (style.showLogo && !style.center) width * .61f else width - 2 * margin
    if (style.layout == WatermarkLayout.CHECK_IN && style.showTime) {
        val checkTimeX = if (style.center) x else if (style.showLogo) width * .29f else margin
        text(facts.time, checkTimeX, if (style.center && style.showLogo) center + bandHeight * .19f else center,
            maximum * .48f, size * 1.6f, style.center)
        text(if (style.showDate) facts.date else "", if (style.center) x else width - margin, center + bandHeight * .32f,
            maximum * .50f, size * .72f, style.center, !style.center)
    } else {
        val first = camera.ifBlank { dateTime }
        val second = dateTime.takeIf { camera.isNotBlank() }.orEmpty()
        val textY = if (style.center && style.showLogo) center + bandHeight * .23f else center
        text(first, x, textY - if (second.isEmpty()) 0f else size * .49f, maximum, size, style.center, !style.center)
        text(second, x, textY + size * .55f, maximum, size * .73f, style.center, !style.center)
    }
}
