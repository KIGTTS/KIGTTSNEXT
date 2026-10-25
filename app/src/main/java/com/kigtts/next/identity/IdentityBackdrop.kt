package com.kigtts.next.identity

import android.graphics.*
import androidx.core.graphics.ColorUtils
import com.kigtts.next.cards.*
import com.kigtts.plugin.api.PolaroidCard

/** Same background pass in native previews and exported cards, before foreground stickers. */
internal fun decorateIdentityBackground(canvas: Canvas, pixels: Bitmap, identity: IdentityCard, repository: StickerRepository) {
    val width = pixels.width; val height = pixels.height
    IdentityPatterns.draw(canvas, repository.context, identity.pattern, width, height,
        ColorUtils.calculateLuminance(pixels.getPixel(width / 2, height / 2)) > .5)
    if (!identity.gradient) { repository.identityFrames.draw(canvas, identity.cardFrameId, width, height, identityInk(pixels)); return }
    // Bounded float32 Gaussian cache input; the original and the blur blend progressively at the bottom.
    val small = Bitmap.createScaledBitmap(pixels, minOf(320, width), (minOf(320, width) * height.toFloat() / width).toInt().coerceAtLeast(1), true)
    val blurred = try { com.kigtts.next.theme.gaussianBlur(small, 4f) } finally { if (small !== pixels) small.recycle() }
    try {
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val layer = Canvas(mask); val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            layer.drawBitmap(blurred, null, Rect(0, 0, width, height), p)
            p.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            p.shader = LinearGradient(0f, height * .3f, 0f, height.toFloat(), Color.TRANSPARENT, Color.WHITE, Shader.TileMode.CLAMP)
            layer.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
            canvas.drawBitmap(mask, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        } finally { mask.recycle() }
    } finally { blurred.recycle() }
    val p = Paint().apply { shader = LinearGradient(0f, height * .25f, 0f, height.toFloat(),
        Color.TRANSPARENT, ColorUtils.setAlphaComponent(identity.gradientColor.toInt(), 208), Shader.TileMode.CLAMP) }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
    repository.identityFrames.draw(canvas, identity.cardFrameId, width, height, identityInk(pixels))
}

internal fun renderIdentityBackground(card: PolaroidCard, identity: IdentityCard, stickers: StickerRepository) =
    PolaroidRenderer.renderBitmap(stickers, card.copy(layers = emptyList()), maximumHeight = 216,
        beforeLayers = { canvas, pixels -> decorateIdentityBackground(canvas, pixels, identity, stickers) })

internal fun identityInk(pixels: Bitmap?): Int {
    if (pixels == null) return Color.WHITE
    var luminance = 0.0; var count = 0
    for (y in 7..9) for (x in 2..7) {
        luminance += ColorUtils.calculateLuminance(pixels.getPixel(pixels.width * x / 10, pixels.height * y / 10)); count++
    }
    return if (luminance / count > .42) 0xFF202020.toInt() else Color.WHITE
}
