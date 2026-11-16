package com.kigtts.next.identity

import android.content.Context
import android.graphics.*
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import androidx.core.graphics.ColorUtils
import com.kigtts.next.media.WebThemeState
import com.kigtts.next.sharing.faceWebTheme
import com.kigtts.next.theme.*
import kotlin.math.max

/** Snapshot the native theme and typeface at export time. */
internal class ProfileLongStyle(val context: Context, private val settings: ThemeSettings) {
    private val theme = faceWebTheme(WebThemeState.current)
    val dark get() = theme.dark
    private fun role(name: String, fallback: Int) = theme.colors[name]?.let { runCatching { Color.parseColor(it) }.getOrNull() } ?: fallback
    val surface = role("background", role("surface", Color.WHITE))
    val tile = role("surface-container-high", 0xffe5eeee.toInt())
    val ink = role("on-surface", Color.BLACK)
    val muted = role("on-surface-variant", ink)
    val primary = role("primary", ink)
    val primaryContainer = role("primary-container", tile)
    val tagBackground = role("secondary-container", tile)
    val tagInk = role("on-secondary-container", ink)
    val radius = (settings.cornerRadius + 8).coerceAtMost(24f) * 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val background = AppearanceBackgrounds.load(context, settings.background, BackgroundBlur.CARD)
    private val font = AppearanceAssets(context).file(settings.font)?.let { AppearanceTypeface(it, settings.fontWeight) }
    private val faces = mutableMapOf<Int, Typeface>()
    val fontScale by lazy { runCatching { font?.heightScale }.getOrNull() ?: 1f }
    private val systemTextScale = context.resources.displayMetrics.let {
        (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16f, it) / (16f * it.density)).coerceIn(.8f, 1.6f)
    }

    fun face(weight: Int): Typeface = faces.getOrPut(weight) {
        val fallback = if (weight >= 500) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        runCatching { font?.forWeight(weight) }.getOrNull() ?: fallback
    }

    fun layout(text: String, size: Float, color: Int, width: Int, lines: Int, weight: Int = 400,
        align: android.text.Layout.Alignment = android.text.Layout.Alignment.ALIGN_NORMAL): StaticLayout {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; textSize = size * fontScale * systemTextScale; typeface = face(weight)
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width.coerceAtLeast(1)).setIncludePad(false)
            .setAlignment(align).setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).build()
    }

    fun text(canvas: Canvas, text: StaticLayout, x: Float, y: Float) {
        canvas.save(); canvas.translate(x, y); text.draw(canvas); canvas.restore()
    }

    fun backdrop(canvas: Canvas, width: Int, fadeStart: Int, fadeEnd: Int) {
        canvas.drawColor(surface)
        val source = background ?: return
        val scale = max(width.toFloat() / source.width, fadeEnd.toFloat() / source.height)
        val w = source.width * scale
        val h = source.height * scale
        canvas.save()
        canvas.clipRect(0, 0, width, fadeEnd)
        val left = (width - w) / 2; val top = (fadeEnd - h) / 2
        paint.reset(); paint.isFilterBitmap = true
        canvas.drawBitmap(source, null, RectF(left, top, left + w, top + h), paint)
        paint.color = ColorUtils.setAlphaComponent(surface, (appearanceBackgroundDim(settings.backgroundDim, BackgroundBlur.CARD) * 255).toInt())
        canvas.drawRect(0f, 0f, width.toFloat(), fadeEnd.toFloat(), paint)
        paint.color = Color.WHITE
        paint.shader = LinearGradient(0f, fadeStart.toFloat(), 0f, fadeEnd.toFloat(),
            surface and 0x00ffffff, surface, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, fadeStart.toFloat(), width.toFloat(), fadeEnd.toFloat(), paint)
        paint.shader = null
        paint.isAntiAlias = true
        canvas.restore()
    }
}
