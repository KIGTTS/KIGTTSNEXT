package com.kigtts.next.ui.cards.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.ui.geometry.Offset
import com.google.zxing.*
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.qrcode.QRCodeMultiReader

internal data class CameraCode(val text: String, val center: Offset?)

/** Points are normalized to the stored image (not the current device or view orientation). */
internal data class CameraCodeFrame(val bitmap: Bitmap, val codes: List<CameraCode>)

/** One scanner for camera frames and selected images. Both polarities are checked in the same frame. */
internal class CameraCodeDecoder {
    private val hints = mapOf(DecodeHintType.TRY_HARDER to true)
    private val multi = QRCodeMultiReader()
    private val reader = MultiFormatReader().also { it.setHints(hints) }

    fun decode(source: LuminanceSource): List<CameraCode> {
        val variants = listOf(source, source.invert())
        val candidates = variants.flatMap { pixels ->
            val hybrid = BinaryBitmap(HybridBinarizer(pixels))
            val found = attempt { multi.decodeMultiple(hybrid, hints).toList() }.orEmpty()
            if (found.isNotEmpty()) found
            else listOfNotNull(attempt { reader.decodeWithState(hybrid) }
                ?: attempt { reader.decodeWithState(BinaryBitmap(GlobalHistogramBinarizer(pixels))) })
        }
        if (candidates.isEmpty() && source.isRotateSupported) {
            val rotated = decodeRotated(source.rotateCounterClockwise())
            return rotated.map { code -> code.copy(center = code.center?.let { Offset(source.width - 1f - it.y, it.x) }) }
        }
        return candidates.mapNotNull(::cameraCode).distinctBy { it.text }.take(12)
    }

    private fun decodeRotated(source: LuminanceSource) = listOf(source, source.invert()).mapNotNull {
        attempt { reader.decodeWithState(BinaryBitmap(HybridBinarizer(it))) }?.let(::cameraCode)
    }.distinctBy { it.text }

    private inline fun <T> attempt(block: () -> T): T? = try { block() } catch (_: ReaderException) { null }
}

private fun cameraCode(result: Result): CameraCode? {
    val text = result.text?.takeIf { it.isNotBlank() && it.length <= 8192 } ?: return null
    val points = result.resultPoints.orEmpty().filter { it.x.isFinite() && it.y.isFinite() }
    val center = when {
        points.size >= 3 && result.barcodeFormat == BarcodeFormat.QR_CODE ->
            // The midpoint of the upper-right and lower-left finder patterns is the QR center.
            Offset((points[0].x + points[2].x) / 2f, (points[0].y + points[2].y) / 2f)
        points.isNotEmpty() -> Offset(points.map { it.x }.average().toFloat(), points.map { it.y }.average().toFloat())
        else -> null
    }
    return CameraCode(text, center)
}

internal fun readCameraCodes(context: Context, uri: Uri): CameraCodeFrame? {
    val resolver = context.contentResolver
    val bitmap = if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val scale = minOf(1f, 1600f / maxOf(info.size.width, info.size.height).coerceAtLeast(1))
            if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1),
                (info.size.height * scale).toInt().coerceAtLeast(1))
        }
    } else {
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 1600) options.inSampleSize *= 2
        options.inJustDecodeBounds = false
        resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, options) } ?: return null
    }
    try {
        val w = bitmap.width; val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val luminance = RGBLuminanceSource(w, h, pixels)
        var codes = CameraCodeDecoder().decode(luminance)
        if (codes.isEmpty()) {
            // Some camera roll barcodes are sideways; restore their decoded points to the original image.
            val rotated = IntArray(pixels.size)
            for (y in 0 until h) for (x in 0 until w) rotated[(w - 1 - x) * h + y] = pixels[y * w + x]
            codes = CameraCodeDecoder().decode(RGBLuminanceSource(h, w, rotated)).map {
                it.copy(center = it.center?.let { point -> Offset(w - 1f - point.y, point.x) })
            }
        }
        if (codes.isNotEmpty()) return CameraCodeFrame(bitmap, codes.map { code ->
            code.copy(center = code.center?.let { Offset(it.x / w, it.y / h) })
        })
    } catch (error: Exception) { bitmap.recycle(); throw error }
    bitmap.recycle()
    return null
}
