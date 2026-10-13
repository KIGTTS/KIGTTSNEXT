package com.kigtts.next.ui.cards

import android.graphics.Bitmap
import com.kigtts.next.media.MediaBitmapDecoder
import java.io.File
import kotlin.math.floor

/** Camera output is baked to the photo window after orientation; gallery imports retain their shape. */
internal fun cropCameraPhoto(source: File, target: File, maximum: Int = 2048) {
    val bitmap = requireNotNull(MediaBitmapDecoder.decode(source, maximum)) { "无法读取拍摄的照片" }
    val aspect = 1072.0 / 1128.0
    val width = minOf(bitmap.width, floor(bitmap.height * aspect).toInt().coerceAtLeast(1))
    val height = minOf(bitmap.height, floor(width / aspect).toInt().coerceAtLeast(1))
    var cropped: Bitmap? = null
    try {
        val output = Bitmap.createBitmap(bitmap, (bitmap.width - width) / 2, (bitmap.height - height) / 2, width, height)
        cropped = output
        target.outputStream().use { check(output.compress(Bitmap.CompressFormat.JPEG, 95, it)) { "照片未能保存" } }
    } catch (failure: Throwable) { target.delete(); throw failure }
    finally { if (cropped !== bitmap) cropped?.recycle(); bitmap.recycle() }
}
