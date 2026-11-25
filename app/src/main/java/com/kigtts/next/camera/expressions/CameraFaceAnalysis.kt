package com.kigtts.next.camera.expressions

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform
import androidx.core.content.ContextCompat
import cn.kigarface.prototype.KigFaceTracker
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal data class CameraFaceFrame(
    val faces: List<ExpressionFace>,
    val source: OutputTransform,
    val uprightToSensor: Matrix,
    val arrivedAt: Long,
) {
    fun isCurrent(now: Long = SystemClock.elapsedRealtime()) = now - arrivedAt in 0..400
    fun toView(target: OutputTransform): Matrix = Matrix().also { CoordinateTransform(source, target).transform(it) }
}

internal class CameraFaceAnalysis(
    context: Context,
    private val onUnavailable: () -> Unit,
    private val onFrame: (CameraFaceFrame) -> Unit,
) : ImageAnalysis.Analyzer, AutoCloseable {
    private val tracker = lazy { KigFaceTracker(context.applicationContext) }
    private val main = ContextCompat.getMainExecutor(context)
    private val closed = AtomicBoolean(false)
    private val scheduled = AtomicBoolean(false)
    private val factory = ImageProxyTransformFactory().apply {
        isUsingRotationDegrees = true
        isUsingCropRect = false
    }
    @Volatile private var current: CameraFaceFrame? = null
    @Volatile private var unavailable = false
    @Volatile var sampleSurface = false
    @Volatile var sampleHistogram: ((ImageProxy) -> Unit)? = null

    fun latest(): CameraFaceFrame? = current?.takeIf { !closed.get() && it.isCurrent() }

    override fun analyze(image: ImageProxy) {
        try {
            if (closed.get()) return
            sampleHistogram?.invoke(image)
            if (unavailable) return
            val arrivedAt = SystemClock.elapsedRealtime()
            val withSurface = sampleSurface
            val result = tracker.value.analyze(image, withSurface)
            val surfaces = if (withSurface) cn.kigarface.prototype.FaceSurfaceSamples.read(result) else emptyMap()
            val upright = sensorToUpright(image.width, image.height, image.imageInfo.rotationDegrees,
                image.imageInfo.sensorToBufferTransformMatrix)
            val inverse = Matrix()
            if (!upright.invert(inverse)) return
            val faces = result.faces().take(12).mapNotNull { face ->
                val dimensions = floatArrayOf(face.left(), face.top(), face.right(), face.bottom(), face.score(), face.opacity())
                if (!dimensions.all(Float::isFinite) || face.right() <= face.left() || face.bottom() <= face.top()) return@mapNotNull null
                fun KigFaceTracker.Point.point() = ExpressionPoint(x(), y())
                val points = listOf(face.leftEye(), face.rightEye(), face.nose(), face.leftMouth(), face.rightMouth())
                if (points.any { !it.x().isFinite() || !it.y().isFinite() }) return@mapNotNull null
                ExpressionFace(face.id(), RectF(face.left(), face.top(), face.right(), face.bottom()),
                    face.opacity().coerceIn(0f, 1f), face.score().coerceIn(0f, 1f),
                    points[0].point(), points[1].point(), points[2].point(), points[3].point(), points[4].point(), surfaces[face.id()]?.let(ExpressionFaceSurface::from))
            }
            current = CameraFaceFrame(faces, factory.getOutputTransform(image), inverse, arrivedAt)
            if (scheduled.compareAndSet(false, true)) main.execute {
                scheduled.set(false)
                latest()?.let(onFrame)
            }
        } catch (failure: LinkageError) {
            disable(failure)
        } catch (failure: Exception) {
            disable(failure)
        } finally { image.close() }
    }

    private fun disable(failure: Throwable) {
        unavailable = true
        current = null
        Log.w("KigFaceAnalysis", "Face analysis unavailable for this camera stream", failure)
        main.execute { if (!closed.get()) onUnavailable() }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        current = null
        executor.execute { if (tracker.isInitialized()) runCatching { tracker.value.close() } }
    }

    companion object {
        val supported get() = Process.is64Bit() && Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a"
        val executor by lazy { Executors.newSingleThreadExecutor { Thread(it, "KigFaceAnalysis") } }
    }
}

internal fun sensorToUpright(width: Int, height: Int, rotation: Int, sensorToImage: Matrix): Matrix {
    val rotate = Matrix().apply {
        setRotate(rotation.toFloat())
        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        mapRect(bounds)
        postTranslate(-bounds.left, -bounds.top)
    }
    return Matrix().apply { setConcat(rotate, sensorToImage) }
}
