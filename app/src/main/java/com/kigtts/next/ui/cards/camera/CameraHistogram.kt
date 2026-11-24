package com.kigtts.next.ui.cards.camera

import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.nio.ByteBuffer
import java.util.concurrent.Executors

/** Pro-only preview luminance, decimated and capped at 5 samples/sec. Never reads or retains a full bitmap. */
internal class CameraHistogram : ImageAnalysis.Analyzer {
    private var lastSample: Long? = null
    var bins by mutableStateOf<IntArray?>(null); private set
    override fun analyze(image: ImageProxy) {
        try { sample(image) } finally { image.close() }
    }

    fun sample(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (lastSample?.let { now - it < 200 } == true) return
        lastSample = now
        val plane = image.planes.firstOrNull() ?: return
        val crop = image.cropRect
        bins = cameraLuminanceBins(plane.buffer, plane.rowStride, plane.pixelStride, crop.left, crop.top, crop.right, crop.bottom)
    }

    companion object { val executor by lazy { Executors.newSingleThreadExecutor() } }
}

internal fun cameraLuminanceBins(buffer: ByteBuffer, rowStride: Int, pixelStride: Int,
    left: Int, top: Int, right: Int, bottom: Int): IntArray {
    val bins = IntArray(32)
    val xStep = ((right - left) / 64).coerceAtLeast(1)
    val yStep = ((bottom - top) / 48).coerceAtLeast(1)
    val origin = buffer.position()
    for (y in top until bottom step yStep) for (x in left until right step xStep) {
        val offset = origin + y * rowStride + x * pixelStride
        if (offset in origin until buffer.limit()) bins[(buffer.get(offset).toInt() and 255) / 8]++
    }
    return bins
}

@Composable
internal fun CameraHistogramOverlay(histogram: CameraHistogram, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Canvas(modifier.size(90.dp, 36.dp).background(Color.Black.copy(alpha = .35f), shape)
        .border(1.dp, Color.White.copy(alpha = .38f), shape).padding(3.dp).testTag("camera-pro-histogram")) {
        val values = histogram.bins ?: return@Canvas
        val peak = (values.maxOrNull() ?: 0).coerceAtLeast(1)
        drawPath(Path().apply {
            moveTo(0f, size.height)
            values.forEachIndexed { index, count ->
                lineTo(size.width * index / values.lastIndex, size.height * (1f - count.toFloat() / peak))
            }
            lineTo(size.width, size.height)
            close()
        }, Color.White.copy(alpha = .90f))
    }
}
