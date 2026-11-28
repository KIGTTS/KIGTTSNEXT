package com.kigtts.next.camera.expressions

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.RectF
import com.kigtts.next.media.MediaBitmapDecoder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64

private const val EDGE = 320
internal const val EMPTY_EXPRESSION_SVG = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 320 320"/>"""

/** Size the sticker once and embed the encoded copy so a pack can be shared independently. */
@Suppress("DEPRECATION")
internal fun expressionStickerImage(file: File): String {
    val bitmap = requireNotNull(MediaBitmapDecoder.decode(file, EDGE))
    return try {
        ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.WEBP, 90, output))
            Base64.getEncoder().encodeToString(output.toByteArray()).also {
                require(it.length <= ExpressionPack.MAX_SVG)
            }
        }
    } finally {
        bitmap.recycle()
    }
}

internal fun expressionBitmapPicture(encoded: String): Picture {
    require(encoded.length in 16..ExpressionPack.MAX_SVG)
    val bytes = Base64.getDecoder().decode(encoded)
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    require(options.outMimeType == "image/webp" && options.outWidth in 1..EDGE && options.outHeight in 1..EDGE)
    val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
    val scale = EDGE.toFloat() / maxOf(bitmap.width, bitmap.height)
    val w = bitmap.width * scale
    val h = bitmap.height * scale
    return Picture().apply {
        val canvas = beginRecording(EDGE, EDGE)
        try {
            canvas.drawBitmap(bitmap, null, RectF((EDGE - w) / 2, (EDGE - h) / 2, (EDGE + w) / 2, (EDGE + h) / 2),
                Paint(Paint.FILTER_BITMAP_FLAG))
        } finally { endRecording() }
    }
}
