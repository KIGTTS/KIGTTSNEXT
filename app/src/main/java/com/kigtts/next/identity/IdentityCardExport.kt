package com.kigtts.next.identity

import android.graphics.*
import com.kigtts.next.cards.*
import com.kigtts.next.i18n.uiCopy
import java.io.File

internal fun renderIdentityCover(identity: IdentityCard, stickers: StickerRepository, background: Bitmap, height: Int): Bitmap {
    val card = requireNotNull(stickers.identityDesigns.get(identity.designId))
    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    return PolaroidRenderer.renderBitmap(stickers, card, maximumHeight = height,
        beforeLayers = { canvas, pixels -> canvas.drawBitmap(background, null, Rect(0, 0, pixels.width, pixels.height), paint) })
}

/** Identity overlays are also drawn into the shared image, not just the settings preview. */
internal fun exportIdentityCard(identity: IdentityCard, stickers: StickerRepository, directory: File,
    includeContactButtons: Boolean = true): File {
    val card = requireNotNull(stickers.identityDesigns.get(identity.designId))
    val background = kotlinx.coroutines.runBlocking { stickers.identityBackgrounds.load(card, identity, 720) }
    val bitmap = renderIdentityCover(identity, stickers, background, 720)
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
        paint.textSize = height * .07f; paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(identity.nickname.ifBlank { stickers.context.uiCopy("profile.export_user") }, x + size + 24, y + size * .4f, paint)
        paint.typeface = Typeface.DEFAULT; paint.textSize = height * .038f
        canvas.drawText(identity.bio.take(60), x + size + 24, y + size * .82f, paint)
        return File(directory, "identity-${identity.id}.png").also { file -> file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
    } finally { bitmap.recycle() }
}
