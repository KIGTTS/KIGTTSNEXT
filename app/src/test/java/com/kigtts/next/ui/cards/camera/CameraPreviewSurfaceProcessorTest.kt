package com.kigtts.next.ui.cards.camera

import android.graphics.SurfaceTexture
import android.opengl.EGLSurface
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.core.impl.CameraInternal
import androidx.core.util.Consumer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.lang.reflect.Proxy
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/** Real CameraX request futures prove release ordering; GL allocation alone is injected, not frame success. */
@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [26, 35])
class CameraPreviewSurfaceProcessorTest {
    private class Worker : CameraPreviewWorker {
        private val tasks = ArrayDeque<Runnable>()
        override val handler = Handler(Looper.getMainLooper())
        var closes = 0
        override val executor = Executor { if (closes == 0) tasks.addLast(it) else it.run() }
        fun drain() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
        override fun close() { closes++ }
    }
    private class Renderer : CameraPreviewRenderer {
        var inputReleases = 0; var outputReleases = 0; var closes = 0; var fail = false
        override fun initialize() {}
        override fun createInput(size: Size): CameraGlInput {
            if (fail) error("GPU allocation refused")
            val texture = SurfaceTexture(0).apply { setDefaultBufferSize(size.width, size.height) }
            return CameraGlInput(0, texture, Surface(texture))
        }
        override fun createOutput(surface: Surface, size: Size): CameraGlOutput {
            // This protocol fixture never calls EGL; no platform EGL14 static initializer is needed.
            val egl = EGLSurface::class.java.getDeclaredConstructor(requireNotNull(Long::class.javaPrimitiveType))
                .apply { isAccessible = true }.newInstance(0L)
            return CameraGlOutput(egl, size)
        }
        override fun latch(input: CameraGlInput, transform: FloatArray) {}
        override fun draw(input: CameraGlInput, output: CameraGlOutput, transform: FloatArray, colors: CameraPreviewUniforms, exposureWarning: Boolean) {}
        override fun releaseInput(input: CameraGlInput) { inputReleases++; input.surface.release(); input.texture.release() }
        override fun releaseOutput(output: CameraGlOutput) { outputReleases++ }
        override fun close() { closes++ }
    }
    private class Output : SurfaceOutput {
        val texture = SurfaceTexture(0); val surface = Surface(texture)
        var closes = 0
        override fun getSurface(executor: Executor, listener: Consumer<SurfaceOutput.Event>) = surface
        override fun getTargets() = CameraEffect.PREVIEW
        override fun getSize() = Size(48, 62)
        override fun updateTransformMatrix(updated: FloatArray, original: FloatArray) { original.copyInto(updated) }
        override fun close() { closes++; surface.release(); texture.release() }
    }
    private fun request(): SurfaceRequest {
        val camera = Proxy.newProxyInstance(CameraInternal::class.java.classLoader, arrayOf(CameraInternal::class.java)) { _, method, _ ->
            when (method.name) { "hashCode" -> 1; "toString" -> "request-test-camera"; else -> null }
        } as CameraInternal
        return SurfaceRequest(Size(48, 62), camera) {}
    }
    private fun input(processor: CameraPreviewSurfaceProcessor, request: SurfaceRequest, worker: Worker) {
        processor.executor.execute { processor.onInputSurface(request) }; worker.drain()
        assertTrue(request.isServiced)
        assertNotNull(request.deferrableSurface.surface.get(1, TimeUnit.SECONDS))
    }

    @Test fun modernSessionCloseStopsOutputsButKeepsGpuAliveUntilTheCameraReturnsItsInputLease() {
        val renderer = Renderer(); val worker = Worker(); val errors = mutableListOf<Throwable>()
        val processor = CameraPreviewSurfaceProcessor(CameraPreviewColors(), errors::add, worker) { renderer }
        val request = request(); request.deferrableSurface.incrementUseCount()
        var hardwareOwned = true
        val output = Output()
        try {
            input(processor, request, worker)
            processor.executor.execute { processor.onOutputSurface(output) }; worker.drain()
            assertTrue(errors.toString(), errors.isEmpty())
            processor.close(); worker.drain()
            assertEquals(1, renderer.outputReleases); assertEquals(1, output.closes)
            assertEquals(0, renderer.inputReleases); assertEquals(0, renderer.closes); assertEquals(0, worker.closes)
            request.deferrableSurface.close(); worker.drain()
            assertEquals(0, renderer.inputReleases)
            request.deferrableSurface.decrementUseCount(); hardwareOwned = false; worker.drain()
            assertEquals(1, renderer.inputReleases); assertEquals(1, renderer.closes); assertEquals(1, worker.closes)
            processor.close(); assertEquals(1, renderer.closes)
        } finally {
            request.deferrableSurface.close()
            if (hardwareOwned) request.deferrableSurface.decrementUseCount()
            processor.close(); worker.drain()
        }
    }
    @Test fun replacementInputsAreIndependentlyReturnedAndALateRequestCannotReopenTheReleasedGpu() {
        val renderer = Renderer(); val worker = Worker(); val errors = mutableListOf<Throwable>()
        val processor = CameraPreviewSurfaceProcessor(CameraPreviewColors(), errors::add, worker) { renderer }
        val first = request(); val next = request()
        try {
            input(processor, first, worker); input(processor, next, worker)
            first.deferrableSurface.close(); worker.drain()
            assertEquals(1, renderer.inputReleases); assertEquals(0, renderer.closes)
            processor.close(); worker.drain(); assertEquals(0, renderer.closes)
            next.deferrableSurface.close(); worker.drain()
            assertEquals(2, renderer.inputReleases); assertEquals(1, renderer.closes)
            val late = request(); processor.onInputSurface(late)
            assertTrue(late.isServiced); assertEquals(1, renderer.closes)
            late.deferrableSurface.close()
            assertTrue(errors.toString(), errors.isEmpty())
        } finally { first.deferrableSurface.close(); next.deferrableSurface.close(); processor.close(); worker.drain() }
    }
    @Test fun allocationFailureIsReportedAndTheRequestIsNotLeftWaitingForASurface() {
        val renderer = Renderer().apply { fail = true }; val errors = mutableListOf<Throwable>(); val worker = Worker()
        val processor = CameraPreviewSurfaceProcessor(CameraPreviewColors(), errors::add, worker) { renderer }
        val request = request()
        try {
            processor.executor.execute {
                assertThrows(ProcessingException::class.java) { processor.onInputSurface(request) }
            }; worker.drain()
            assertTrue(request.isServiced); assertEquals(1, errors.size); assertNotNull(processor.failure)
            processor.close(); worker.drain(); assertEquals(1, renderer.closes)
        } finally { request.deferrableSurface.close(); processor.close(); worker.drain() }
    }
    @Test fun previewEffectCannotTintThePreservedJpegOrTheRawMotionVideo() {
        val worker = Worker()
        val processor = CameraPreviewSurfaceProcessor(CameraPreviewColors(), { fail(it.toString()) }, worker)
        val effect = CameraPreviewEffect(processor)
        try {
            assertEquals(CameraEffect.PREVIEW, effect.targets)
            assertEquals(0, effect.targets and CameraEffect.IMAGE_CAPTURE)
            assertEquals(0, effect.targets and CameraEffect.VIDEO_CAPTURE)
        } finally { effect.close(); worker.drain() }
    }
    @Test fun updatingFilterDoesNotResetExposureWarning() {
        val colors = CameraPreviewColors()
        assertFalse(colors.exposureWarning)
        colors.exposureWarning = true
        colors.update(PhotoFilters.coefficients(PhotoFilter.ORIGINAL))
        assertTrue(colors.exposureWarning)
        colors.exposureWarning = false
        assertFalse(colors.exposureWarning)
    }

    @Test fun regularVideoUsesTheSameEffectForPreviewAndEncoderWithoutChangingStillCapture() {
        val worker = Worker()
        val processor = CameraPreviewSurfaceProcessor(CameraPreviewColors(), { fail(it.toString()) }, worker)
        val effect = CameraPreviewEffect(processor, video = true)
        try {
            assertEquals(CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE, effect.targets)
            assertEquals(0, effect.targets and CameraEffect.IMAGE_CAPTURE)
        } finally { effect.close(); worker.drain() }
    }
}
