package com.kigtts.next.desktop

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.PathParser
import com.kigtts.next.identity.AvatarFrames
import com.kigtts.next.theme.ThemeSettingsStore
import com.kigtts.next.theme.themeColors
import kotlin.math.roundToInt

internal data class DesktopAppearance(val colors: ColorScheme, val corner: Float) {
    companion object {
        fun load(context: Context): DesktopAppearance {
            val settings = ThemeSettingsStore(context).load()
            val systemDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            val dark = settings.mode.usesDark(systemDark)
            val wallpaper = if (settings.wallpaperEnabled && Build.VERSION.SDK_INT >= 31) {
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else null
            return DesktopAppearance(themeColors(settings, wallpaper, dark), settings.cornerRadius)
        }
    }
}

internal fun desktopBackground(width: Float, height: Float, color: Int, corners: List<Float>): Bitmap {
    val pixelWidth = (width * 1.5f).roundToInt().coerceIn(2, 640)
    val pixelHeight = (height * 1.5f).roundToInt().coerceIn(2, 420)
    return Bitmap.createBitmap(pixelWidth, pixelHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
        val radii = corners.flatMap { radius -> listOf(radius * 1.5f, radius * 1.5f) }.toFloatArray()
        val path = android.graphics.Path().apply {
            addRoundRect(RectF(0f, 0f, pixelWidth.toFloat(), pixelHeight.toFloat()), radii, android.graphics.Path.Direction.CW)
        }
        Canvas(bitmap).drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }
}

internal fun flowerIcon(context: Context, @DrawableRes icon: Int, color: Int, ink: Int): Bitmap {
    val edge = 108
    val space = Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(space)
    val path = PathParser.createPathFromPathData(AvatarFrames.svgPath("flower")).apply {
        transform(Matrix().apply { setScale(96f, 96f); postTranslate(6f, 6f) })
    }
    canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    desktopGlyph(context, icon, ink, 44)?.let { glyph ->
        canvas.drawBitmap(glyph, 32f, 32f, null)
        glyph.recycle()
    }
    return space
}

internal fun desktopGlyph(context: Context, @DrawableRes icon: Int, ink: Int, edge: Int = 64): Bitmap? {
    val drawable = ResourcesCompat.getDrawable(context.resources, icon, context.theme)?.mutate() ?: return null
    drawable.setTint(ink)
    drawable.setBounds(0, 0, edge, edge)
    return Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888).also { drawable.draw(Canvas(it)) }
}
