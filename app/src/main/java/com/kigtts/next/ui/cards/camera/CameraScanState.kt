package com.kigtts.next.ui.cards.camera

import android.content.Context
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.core.content.ContextCompat

/** Lives with the camera session; all presentation state is written on the main thread. */
internal class CameraScanState(context: Context) : AutoCloseable {
    var frame by mutableStateOf<CameraCodeFrame?>(null); private set
    var selected by mutableStateOf<String?>(null); private set
    val showing get() = frame != null || selected != null
    private var view: PreviewView? = null
    private var epoch = 0L
    private var importing = false
    private var dismissed = emptySet<String>()
    private var absentFrames = 0
    val analyzer = CameraBarcodeReader { generation, codes -> ContextCompat.getMainExecutor(context).execute {
        if (epoch == generation && !showing) acceptLive(codes)
    } }

    fun bind(view: PreviewView?, generation: Long) {
        clear()
        absentFrames = 0
        this.view = view
        epoch = generation
        analyzer.changeSession(generation, view != null && !importing)
    }

    fun setImporting(value: Boolean) {
        importing = value
        analyzer.pause(value || view == null || showing)
    }

    private fun acceptLive(codes: List<CameraCode>) {
        val preview = view ?: return
        if (codes.none { it.text in dismissed }) {
            if (++absentFrames >= 3) dismissed = emptySet()
        } else absentFrames = 0
        val candidates = codes.filterNot { it.text in dismissed }
        if (candidates.isEmpty()) return
        if (candidates.size == 1) { choose(candidates.single().text); return }
        val transform = preview.sensorToViewTransform ?: return
        val w = preview.width; val h = preview.height
        if (w <= 0 || h <= 0) return
        val points = candidates.mapNotNull { code -> code.center?.let { point ->
            val xy = floatArrayOf(point.x, point.y)
            transform.mapPoints(xy)
            val x = xy[0] / w; val y = xy[1] / h
            if (x in 0f..1f && y in 0f..1f) code.copy(center = Offset(x, y)) else null
        } }
        if (points.size == 1) { choose(points.single().text); return }
        if (points.isEmpty()) return
        // Capture once when entering selection. The feed stays bound for immediate resume.
        val image = cameraPreviewSnapshot(preview) ?: return
        analyzer.pause(true)
        frame = CameraCodeFrame(image, points)
    }

    fun pick(value: CameraCodeFrame) {
        clear()
        if (value.codes.size == 1) {
            value.bitmap.recycle()
            choose(value.codes.single().text)
        } else {
            analyzer.pause(true)
            frame = value
        }
    }

    fun choose(value: String) { analyzer.pause(true); selected = value }

    fun clear() {
        dismissed = frame?.codes?.mapTo(mutableSetOf()) { it.text } ?: selected?.let(::setOf) ?: dismissed
        absentFrames = 0
        selected = null
        frame = null // A Compose/RenderThread display list may still hold the previous bitmap.
        analyzer.pause(view == null || importing)
    }

    override fun close() { bind(null, epoch + 1); analyzer.close() }
}
