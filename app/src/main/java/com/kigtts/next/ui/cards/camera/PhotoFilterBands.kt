package com.kigtts.next.ui.cards.camera

import android.graphics.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Native Skia applies the preview's color matrix in bounded bands, without allocating a second full photograph. */
internal suspend fun applyPhotoFilterBands(image: Bitmap, filter: PhotoFilter, strength: Float = 1f) {
    if (filter == PhotoFilter.ORIGINAL || strength <= 0f) return
    currentCoroutineContext().ensureActive()
    require(image.isMutable)
    val height = minOf(32, image.height)
    val source = Bitmap.createBitmap(image.width, height, Bitmap.Config.ARGB_8888)
    val output = try { Bitmap.createBitmap(image.width, height, Bitmap.Config.ARGB_8888) }
        catch (failure: Throwable) { source.recycle(); throw failure }
    try {
        val canvas = Canvas(output)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(PhotoFilters.matrix(filter, strength))
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
        }
        val pixels = IntArray(image.width * height)
        var y = 0
        while (y < image.height) {
            currentCoroutineContext().ensureActive()
            val rows = minOf(height, image.height - y)
            image.getPixels(pixels, 0, image.width, 0, y, image.width, rows)
            source.setPixels(pixels, 0, image.width, 0, 0, image.width, rows)
            canvas.drawBitmap(source, 0f, 0f, paint)
            output.getPixels(pixels, 0, image.width, 0, 0, image.width, rows)
            image.setPixels(pixels, 0, image.width, 0, y, image.width, rows)
            y += rows
        }
    } finally { source.recycle(); output.recycle() }
}
