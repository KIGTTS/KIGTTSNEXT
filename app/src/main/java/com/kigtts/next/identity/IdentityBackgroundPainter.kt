package com.kigtts.next.identity

import android.graphics.*
import androidx.core.graphics.ColorUtils
import com.kigtts.next.cards.StickerRepository
import com.kigtts.plugin.api.PhotoBackdropMode
import com.kigtts.plugin.api.PolaroidCard

/** Prepared off-thread once. Drawing a new photograph pose never decodes, blurs or allocates pixels. */
internal class IdentityBackgroundPainter(
    private val photo: Bitmap?, private val backdrop: Bitmap?, private val softPhoto: Bitmap?,
    private val artwork: Picture, private val frame: Picture, private val gradient: Boolean,
    private val gradientColor: Int, val ink: Int
) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val window = RectF()
    private val destination = RectF()
    private var shaderHeight = -1f
    private var mask: Shader? = null
    private var tint: Shader? = null

    fun draw(canvas: Canvas, width: Float, height: Float, card: PolaroidCard) {
        window.set(0f, 0f, width, height)
        canvas.save(); canvas.clipRect(window)
        canvas.drawColor(card.frameColor.toInt())
        drawPhoto(canvas, photo, width, height, card)
        canvas.drawPicture(artwork, window)
        if (gradient) {
            if (height != shaderHeight) {
                shaderHeight = height
                mask = LinearGradient(0f, height * .3f, 0f, height, Color.TRANSPARENT, Color.WHITE, Shader.TileMode.CLAMP)
                tint = LinearGradient(0f, height * .25f, 0f, height, Color.TRANSPARENT,
                    ColorUtils.setAlphaComponent(gradientColor, 208), Shader.TileMode.CLAMP)
            }
            // Only the soft layer is blurred; the unmasked photograph remains full resolution.
            val layer = canvas.saveLayer(window, null)
            canvas.drawColor(card.frameColor.toInt())
            drawPhoto(canvas, softPhoto ?: photo, width, height, card)
            canvas.drawPicture(artwork, window)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN); paint.shader = mask
            canvas.drawRect(window, paint)
            paint.xfermode = null; paint.shader = null; canvas.restoreToCount(layer)
            paint.shader = tint; canvas.drawRect(window, paint); paint.shader = null
        }
        canvas.drawPicture(frame, window)
        canvas.restore()
    }

    private fun drawPhoto(canvas: Canvas, source: Bitmap?, width: Float, height: Float, card: PolaroidCard) {
        if (source == null) return
        canvas.drawColor(0xFFB0B0B0.toInt())
        if (card.photoBackdrop == PhotoBackdropMode.BLUR && backdrop != null) {
            val scale = maxOf(width / backdrop.width, height / backdrop.height)
            val w = backdrop.width * scale; val h = backdrop.height * scale
            destination.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
            canvas.drawBitmap(backdrop, null, destination, paint)
        }
        val scale = minOf(width / source.width, height / source.height) * card.photoZoom.toFloat()
        val w = source.width * scale; val h = source.height * scale
        val x = width * card.photoX.toFloat(); val y = height * card.photoY.toFloat()
        destination.set(x - w / 2, y - h / 2, x + w / 2, y + h / 2)
        canvas.drawBitmap(source, null, destination, paint)
    }

    companion object {
        fun create(card: PolaroidCard, identity: IdentityCard, repository: StickerRepository,
            photo: Bitmap?, backdrop: Bitmap?, softPhoto: Bitmap?): IdentityBackgroundPainter {
            val light = if (photo == null) ColorUtils.calculateLuminance(card.frameColor.toInt()) > .5
                else ColorUtils.calculateLuminance(photo.getPixel(photo.width / 2, photo.height / 2)) > .5
            val artwork = Picture().apply { IdentityPatterns.draw(beginRecording(1000, 563), repository.context, identity.pattern, 1000, 563, light); endRecording() }
            val ink = if (ColorUtils.calculateLuminance(if (identity.gradient) identity.gradientColor.toInt()
                else photo?.getPixel(photo.width / 2, photo.height * 4 / 5) ?: card.frameColor.toInt()) > .42) 0xFF202020.toInt() else Color.WHITE
            val frame = Picture().apply { repository.identityFrames.draw(beginRecording(1600, 900), identity.cardFrameId, 1600, 900, ink); endRecording() }
            return IdentityBackgroundPainter(photo, backdrop, softPhoto, artwork, frame, identity.gradient, identity.gradientColor.toInt(), ink)
        }
    }
}
