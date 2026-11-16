package com.kigtts.next.identity

import android.graphics.*
import com.kigtts.next.cards.*
import java.io.File

/** Identity overlays are also drawn into the shared image, not just the settings preview. */
internal fun exportIdentityCard(identity: IdentityCard, stickers: StickerRepository, directory: File,
    includeContactButtons: Boolean = true, typeface: ((Int) -> Typeface)? = null, textScale: Float = 1f): File {
    val card = requireNotNull(stickers.identityDesigns.get(identity.designId))
    val background = kotlinx.coroutines.runBlocking { stickers.identityBackgrounds.load(card, identity, 720) }
    val bitmap = PolaroidRenderer.renderBitmap(stickers, card, maximumHeight = 720,
        beforeLayers = { canvas, pixels -> canvas.drawBitmap(background, null, Rect(0, 0, pixels.width, pixels.height), Paint(Paint.FILTER_BITMAP_FLAG)) })
    try {
        val canvas = Canvas(bitmap); val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val width = bitmap.width.toFloat(); val height = bitmap.height.toFloat()
        val size = height * .22f; val x = width * .06f; val y = height * .67f
        identity.avatar?.let(stickers::imageFile)?.let { file ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(file.path, options)
            options.inSampleSize = (maxOf(options.outWidth, options.outHeight) / 256).coerceAtLeast(1); options.inJustDecodeBounds = false
            BitmapFactory.decodeFile(file.path, options)?.let { avatar ->
                try { canvas.save(); canvas.clipPath(androidx.core.graphics.PathParser.createPathFromPathData(AvatarFrames.svgPath(identity.avatarFrame)).apply {
                        transform(Matrix().apply { setScale(size, size); postTranslate(x, y) }) })
                    canvas.drawBitmap(avatar, null, RectF(x, y, x + size, y + size), paint); canvas.restore() } finally { avatar.recycle() }
            }
        }
        paint.color = identityInk(background)
        if (includeContactButtons) drawIdentityContacts(canvas, identity, stickers, height)
        paint.setShadowLayer(height * .006f, 0f, height * .002f, 0x88000000.toInt())
        paint.textSize = height * .07f * textScale; paint.typeface = typeface?.invoke(700) ?: Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(identity.nickname.ifBlank { "KIGTTS 用户" }, x + size + 24, y + size * .4f, paint)
        paint.typeface = typeface?.invoke(400) ?: Typeface.DEFAULT; paint.textSize = height * .038f * textScale
        canvas.drawText(identity.bio.take(60), x + size + 24, y + size * .82f, paint)
        return File(directory, "identity-${identity.id}.png").also { file -> file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
    } finally { bitmap.recycle() }
}
