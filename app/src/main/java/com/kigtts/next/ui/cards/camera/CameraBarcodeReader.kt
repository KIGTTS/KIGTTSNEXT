package com.kigtts.next.ui.cards.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

/** A throttled luminance-only analyzer; every proxy is released, including failures. */
internal class CameraBarcodeReader(private val result: (String) -> Unit) : ImageAnalysis.Analyzer, AutoCloseable {
    val executor = Executors.newSingleThreadExecutor()
    private val reader = MultiFormatReader().apply { setHints(mapOf(DecodeHintType.TRY_HARDER to true, DecodeHintType.CHARACTER_SET to "UTF-8")) }
    private var last = 0L
    override fun analyze(image: ImageProxy) {
        try {
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - last < 350) return
            last = now
            val plane = image.planes.first()
            val bytes = ByteArray(image.width * image.height)
            val data = plane.buffer
            if (plane.pixelStride == 1) for (y in 0 until image.height) {
                data.position(y * plane.rowStride); data.get(bytes, y * image.width, image.width)
            } else for (y in 0 until image.height) for (x in 0 until image.width)
                bytes[y * image.width + x] = data.get(y * plane.rowStride + x * plane.pixelStride)
            val source = PlanarYUVLuminanceSource(bytes, image.width, image.height, 0, 0, image.width, image.height, false)
            val decoded = decode(source) ?: run {
                val rotated = ByteArray(bytes.size)
                for (y in 0 until image.height) for (x in 0 until image.width)
                    rotated[(image.width - 1 - x) * image.height + y] = bytes[y * image.width + x]
                decode(PlanarYUVLuminanceSource(rotated, image.height, image.width, 0, 0, image.height, image.width, false))
            }
            decoded?.text?.takeIf { it.isNotBlank() && it.length <= 8192 }?.let(result)
        } catch (_: Exception) { /* An unreadable frame is not an error state. */ }
        finally { reader.reset(); image.close() }
    }
    private fun decode(source: LuminanceSource) = try { reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))) } catch (_: ReaderException) { null }
    override fun close() { executor.shutdownNow() }
}
