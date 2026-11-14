package com.kigtts.next.ui.cards.camera

import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.ui.geometry.Offset
import com.google.zxing.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal class CameraBarcodeReader(private val result: (Long, List<CameraCode>) -> Unit) : ImageAnalysis.Analyzer, AutoCloseable {
    val executor = Executors.newSingleThreadExecutor()
    private val decoder = CameraCodeDecoder()
    private val paused = AtomicBoolean(true)
    private val epoch = AtomicLong()
    private var last = 0L
    private var lastEpoch = -1L
    fun changeSession(value: Long, enabled: Boolean) { paused.set(true); epoch.set(value); paused.set(!enabled) }
    fun pause(value: Boolean) { paused.set(value) }

    override fun analyze(image: ImageProxy) {
        try {
            if (paused.get()) return
            val generation = epoch.get()
            val now = android.os.SystemClock.elapsedRealtime()
            if (generation == lastEpoch && now - last < 235) return
            last = now; lastEpoch = generation
            val crop = image.cropRect
            val width = crop.width(); val height = crop.height()
            if (width < 1 || height < 1) return
            val plane = image.planes.first()
            val bytes = ByteArray(width * height)
            val data = plane.buffer.duplicate()
            val base = data.position()
            for (y in 0 until height) {
                val start = base + (crop.top + y) * plane.rowStride + crop.left * plane.pixelStride
                if (plane.pixelStride == 1) {
                    data.position(start); data.get(bytes, y * width, width)
                } else for (x in 0 until width) bytes[y * width + x] = data.get(start + x * plane.pixelStride)
            }
            val source = PlanarYUVLuminanceSource(bytes, width, height, 0, 0, width, height, false)
            val cameraToSensor = Matrix()
            val canMap = image.imageInfo.sensorToBufferTransformMatrix.invert(cameraToSensor)
            val found = decoder.decode(source).ifEmpty {
                val rotated = ByteArray(bytes.size)
                for (y in 0 until height) for (x in 0 until width) rotated[(width - 1 - x) * height + y] = bytes[y * width + x]
                decoder.decode(PlanarYUVLuminanceSource(rotated, height, width, 0, 0, height, width, false)).map { code ->
                    code.copy(center = code.center?.let { Offset(width - 1f - it.y, it.x) })
                }
            }
            val codes = found.map { code -> code.copy(center = code.center?.takeIf { canMap }?.let { point ->
                val coords = floatArrayOf(point.x + crop.left, point.y + crop.top)
                cameraToSensor.mapPoints(coords)
                Offset(coords[0], coords[1])
            }) }
            if (!paused.get() && epoch.get() == generation) result(generation, codes)
        } catch (_: Exception) { /* An unreadable frame is not an error state. */ }
        finally { image.close() }
    }
    override fun close() { paused.set(true); executor.shutdown() }
}
