package com.kigtts.next.identity

import android.graphics.*
import com.kigtts.next.cards.*
import java.io.File

/** Identity overlays are also drawn into the shared image, not just the settings preview. */
internal fun exportIdentityCard(identity: IdentityCard, stickers: StickerRepository, directory: File): File {
    val card = requireNotNull(stickers.cards.value.firstOrNull { it.id == identity.designId })
    val bitmap = PolaroidRenderer.renderBitmap(stickers, card, maximumHeight = 720)
    try {
        val canvas = Canvas(bitmap); val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val width = bitmap.width.toFloat(); val height = bitmap.height.toFloat()
        if (identity.gradient) {
            paint.shader = LinearGradient(0f, height * .3f, 0f, height, Color.TRANSPARENT, 0xDD000000.toInt(), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, width, height, paint); paint.shader = null
        }
        val size = height * .22f; val x = width * .06f; val y = height * .67f
        identity.avatar?.let(stickers::imageFile)?.let { file ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(file.path, options)
            options.inSampleSize = (maxOf(options.outWidth, options.outHeight) / 256).coerceAtLeast(1); options.inJustDecodeBounds = false
            BitmapFactory.decodeFile(file.path, options)?.let { avatar ->
                try { canvas.save(); canvas.clipPath(Path().apply { addCircle(x + size / 2, y + size / 2, size / 2, Path.Direction.CW) })
                    canvas.drawBitmap(avatar, null, RectF(x, y, x + size, y + size), paint); canvas.restore() } finally { avatar.recycle() }
            }
        }
        paint.color = if (!identity.gradient && card.imageId == null && androidx.core.graphics.ColorUtils.calculateLuminance(card.frameColor.toInt()) > .5) 0xFF202020.toInt() else Color.WHITE
        val iconSize = (height * .085f).toInt()
        identity.contacts.take(8).forEachIndexed { index, link ->
            androidx.core.content.ContextCompat.getDrawable(stickers.context, socialPlatform(link).icon)?.mutate()?.let { icon ->
                icon.setTint(paint.color)
                val left = (height * .06f + index * height * .15f).toInt()
                val top = (height * .06f).toInt()
                icon.setBounds(left, top, left + iconSize, top + iconSize); icon.draw(canvas)
            }
        }
        paint.textSize = height * .07f; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(identity.nickname.ifBlank { "KIGTTS 用户" }, x + size + 24, y + size * .4f, paint)
        paint.typeface = Typeface.DEFAULT; paint.textSize = height * .038f
        canvas.drawText(identity.bio.take(60), x + size + 24, y + size * .82f, paint)
        return File(directory, "identity-${identity.id}.png").also { file -> file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
    } finally { bitmap.recycle() }
}
