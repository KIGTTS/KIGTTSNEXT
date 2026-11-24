package com.kigtts.next.camera.expressions

import android.os.SystemClock
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import cn.kigarface.prototype.FocusPolicy
import java.util.concurrent.TimeUnit

internal class CameraFaceFocus {
    private val policy = FocusPolicy()
    private var lockedId: Long? = null

    fun manual() { policy.manual(SystemClock.elapsedRealtime()); lockedId = null }
    fun reset() { policy.reset(); lockedId = null }

    fun update(camera: Camera, view: PreviewView, frame: CameraFaceFrame) {
        val target = view.outputTransform ?: return
        if (view.width <= 0 || view.height <= 0) return
        val mapping = frame.toView(target)
        val candidates = frame.faces.filter { it.confidence >= .5f && it.opacity >= .65f }.mapNotNull { face ->
            val point = floatArrayOf(face.nose.x, face.nose.y)
            mapping.mapPoints(point)
            val x = point[0] / view.width
            val y = point[1] / view.height
            if (x !in 0f..1f || y !in 0f..1f) null else Candidate(face, x, y)
        }
        val selection = candidates.firstOrNull { it.face.id == lockedId }
            ?: candidates.maxByOrNull { it.rank }
            ?: return
        val face = selection.face
        lockedId = face.id
        val scale = mapping.mapRadius(face.bounds.width()) / view.width
        val now = SystemClock.elapsedRealtime()
        if (!policy.shouldRequest(now, selection.x, selection.y, scale, face.confidence)) return
        val action = FocusMeteringAction.Builder(view.meteringPointFactory.createPoint(
            selection.x * view.width, selection.y * view.height, .12f), FocusMeteringAction.FLAG_AF)
            .setAutoCancelDuration(2, TimeUnit.SECONDS).build()
        if (!camera.cameraInfo.isFocusMeteringSupported(action)) return
        val request = policy.requested(now, selection.x, selection.y, scale)
        try {
            val future = camera.cameraControl.startFocusAndMetering(action)
            future.addListener({ policy.completed(request, runCatching { future.get().isFocusSuccessful }.getOrDefault(false)) },
                ContextCompat.getMainExecutor(view.context))
        } catch (_: IllegalStateException) { policy.completed(request, false) }
    }

    private data class Candidate(val face: ExpressionFace, val x: Float, val y: Float) {
        val rank get() = face.bounds.width() * face.bounds.height() * face.confidence /
            (1f + 4f * ((x - .5f) * (x - .5f) + (y - .5f) * (y - .5f)))
    }
}
