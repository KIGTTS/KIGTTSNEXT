package com.kigtts.next.ui.cards.camera

import android.util.Log
import androidx.camera.core.*
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** CameraX owns the input lease; closing a session waits for its final surface-result callbacks. */
internal class CameraPreviewSurfaceProcessor(val colors: CameraPreviewColors, private val onFailure: (Throwable) -> Unit,
    private val worker: CameraPreviewWorker = CameraPreviewGlWorker(),
    private val createRenderer: () -> CameraPreviewRenderer = { CameraPreviewGlRenderer() }) : SurfaceProcessor, Closeable {
    private class Input(val request: SurfaceRequest, val gl: CameraGlInput)
    val executor get() = worker.executor
    private val closing = AtomicBoolean(false)
    private val problem = AtomicReference<Throwable?>(null)
    val failure: Throwable? get() = problem.get()
    private var renderer: CameraPreviewRenderer? = null
    private val inputs = linkedSetOf<Input>()
    private val outputs = linkedMapOf<SurfaceOutput, CameraGlOutput>()
    private var current: Input? = null
    private var released = false
    private val originalTransform = FloatArray(16)
    private val outputTransform = FloatArray(16)
    private var renderedFirstFrame = false

    override fun onInputSurface(request: SurfaceRequest) {
        if (closing.get()) { request.willNotProvideSurface(); return }
        failure?.let { request.willNotProvideSurface(); throw processingFailure(it) }
        var input: Input? = null
        try {
            val gl = gl()
            input = Input(request, gl.createInput(request.resolution))
            val attached = input
            inputs += attached; current = attached
            attached.gl.texture.setOnFrameAvailableListener({ render(attached) }, worker.handler)
            request.provideSurface(attached.gl.surface, executor) {
                if (inputs.remove(attached)) {
                    if (current === attached) current = null
                    runCatching { renderer?.releaseInput(attached.gl) }.onFailure(::reportFailure)
                }
                finishReleaseIfReady()
            }
            Log.d(CAMERA_LOG_TAG, "GPU preview input ${request.resolution}; liveInputs=${inputs.size}")
        } catch (failure: Exception) {
            input?.let { attached ->
                if (inputs.remove(attached)) runCatching { renderer?.releaseInput(attached.gl) }
                if (current === attached) current = null
            }
            request.willNotProvideSurface(); reportFailure(failure)
            throw processingFailure(failure)
        }
    }
    override fun onOutputSurface(surfaceOutput: SurfaceOutput) {
        if (closing.get()) { surfaceOutput.close(); return }
        failure?.let { surfaceOutput.close(); throw processingFailure(it) }
        try {
            val surface = surfaceOutput.getSurface(executor) { event ->
                if (event.eventCode == SurfaceOutput.Event.EVENT_REQUEST_CLOSE) removeOutput(surfaceOutput)
            }
            outputs[surfaceOutput] = gl().createOutput(surface, surfaceOutput.size)
        } catch (failure: Exception) {
            removeOutput(surfaceOutput); reportFailure(failure)
            throw processingFailure(failure)
        }
    }
    private fun gl(): CameraPreviewRenderer = renderer ?: createRenderer().also {
        it.initialize(); renderer = it
    }
    private fun render(input: Input) {
        if (closing.get() || failure != null || input !in inputs) return
        try {
            val gl = gl()
            // Consume old input frames during replacement, but never paint them over the new session.
            gl.latch(input.gl, originalTransform)
            if (current !== input) return
            val values = colors.snapshot()
            val exposureWarning = colors.exposureWarning
            outputs.forEach { (output, target) ->
                output.updateTransformMatrix(outputTransform, originalTransform)
                gl.draw(input.gl, target, outputTransform, values, exposureWarning && output.targets and CameraEffect.PREVIEW != 0)
            }
            if (!renderedFirstFrame && outputs.isNotEmpty()) {
                renderedFirstFrame = true; Log.d(CAMERA_LOG_TAG, "GPU preview first filtered frame")
            }
        } catch (failure: Exception) {
            reportFailure(failure)
            // CameraX will ask for another input, where a ProcessingException reports the failure.
            input.request.invalidate()
        }
    }
    private fun removeOutput(output: SurfaceOutput) {
        outputs.remove(output)?.let { target -> runCatching { renderer?.releaseOutput(target) }.onFailure(::reportFailure) }
        output.close()
    }
    fun reportFailure(failure: Throwable) {
        if (problem.compareAndSet(null, failure)) {
            Log.w(CAMERA_LOG_TAG, "GPU preview processing failed", failure)
            onFailure(failure)
        }
    }
    override fun close() {
        if (!closing.compareAndSet(false, true)) return
        executor.execute {
            outputs.keys.toList().forEach(::removeOutput)
            current = null
            finishReleaseIfReady()
        }
    }
    private fun finishReleaseIfReady() {
        if (!closing.get() || inputs.isNotEmpty() || released) return
        released = true
        runCatching { renderer?.close() }.onFailure { Log.w(CAMERA_LOG_TAG, "GPU preview release failed", it) }
        renderer = null
        Log.d(CAMERA_LOG_TAG, "GPU preview released after all camera input leases returned")
        worker.close()
    }
    private fun processingFailure(cause: Throwable) = ProcessingException().apply { initCause(cause) }
}
