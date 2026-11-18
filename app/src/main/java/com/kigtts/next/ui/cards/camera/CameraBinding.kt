package com.kigtts.next.ui.cards.camera

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.util.Log
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.*
import androidx.camera.extensions.*
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class CameraBinding(val camera: Camera, val preview: Preview, val capture: ImageCapture?,
    val lease: CameraSessionLease, val processing: CameraProcessing = CameraProcessing.STANDARD,
    val motionCapture: VideoCapture<Recorder>? = null, val previewEffect: CameraPreviewEffect? = null, val analysis: ImageAnalysis? = null,
    val histogram: CameraHistogram? = null) {
    fun release() { analysis?.clearAnalyzer(); try { lease.close() } finally { try { preview.setSurfaceProvider(null) } finally { previewEffect?.close() } } }
}

@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
internal suspend fun bindPolaroidCamera(context: Context, provider: ProcessCameraProvider, owner: LifecycleOwner,
    selector: CameraSelector, view: PreviewView, rotation: Int, processing: CameraProcessing = CameraProcessing.STANDARD,
    metadata: CameraCaptureSession.CaptureCallback? = null, motion: Boolean = false,
    previewColors: CameraPreviewColors = CameraPreviewColors(), videoMode: Boolean = false, barcode: CameraBarcodeReader? = null,
    histogram: CameraHistogram? = null, previewFailure: (Throwable) -> Unit = {}): CameraBinding {
    val viewport = requireNotNull(view.getViewPort(rotation)) { "相机预览尚未就绪" }
    // Ordinary capture does not initialize a vendor extension or wait for its metadata discovery.
    val requestedProcessing = if (motion || videoMode || barcode != null) CameraProcessing.STANDARD else processing
    val manager = if (requestedProcessing == CameraProcessing.STANDARD) null else try { ExtensionsManager.getInstanceAsync(context, provider).awaitCamera(context) }
        catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { null }
    val available = requestedProcessing.extensionMode?.let { mode -> runCatching { manager?.isExtensionAvailable(selector, mode) == true }.getOrDefault(false) } ?: false
    val modes = cameraExtensionPlan(requestedProcessing, available)
    var failure: Exception? = null
    var skipExtensions = false
    var skipVideo = false
    var skipPreviewEffect = false
    // Preserve high-resolution/quality preference. Only the explicitly selected OEM mode may precede normal fallback.
    val videoQualities = when {
        videoMode -> listOf(Quality.FHD, Quality.HD, Quality.SD)
        motion -> listOf(Quality.FHD, Quality.HD, null)
        else -> listOf(null)
    }
    val photoMode = !videoMode && barcode == null
    val histogramChoices = if (histogram != null) listOf(histogram, null) else listOf(null)
    val videoFilterAttempts = if (videoMode) listOf(true, false) else listOf(false)
    for (videoFilter in videoFilterAttempts) for (quality in videoQualities) for (mode in modes)
        for (highResolution in if (photoMode) listOf(true, false) else listOf(false))
        for (histogramChoice in histogramChoices) {
        currentCoroutineContext().ensureActive()
        if (mode != null && skipExtensions) continue
        if (quality != null && skipVideo) continue
        if (videoFilter && skipPreviewEffect) continue
        // A previous OPEN/STREAMING value must never make a new session look ready before its first frame.
        awaitCameraPreviewIdle(view.previewStreamState)
        // Encoded SDR RGB matches the byte-domain ColorMatrix used for the independently saved JPEG.
        val previewBuilder = Preview.Builder().setTargetRotation(rotation).setDynamicRange(DynamicRange.SDR)
        if (mode == null && metadata != null) Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(metadata)
        val preview = previewBuilder.build()
        val resolution = ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
            .setAllowedResolutionMode(if (highResolution) ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE else ResolutionSelector.PREFER_CAPTURE_RATE_OVER_HIGHER_RESOLUTION)
            .setResolutionFilter { sizes, _ -> sizes.filter { it.width.toLong() * it.height <= PhotoFilterWriter.MAX_PIXELS } }.build()
        val capture = if (photoMode) ImageCapture.Builder().setTargetRotation(rotation).setJpegQuality(98)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).setResolutionSelector(resolution).build() else null
        val video = quality?.let { selected ->
            val recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(selected)).build()
            VideoCapture.Builder(recorder).setTargetRotation(rotation)
                .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY).build()
        }
        val analysis = when {
            barcode != null -> cameraAnalysis(640, 480).also { it.setAnalyzer(barcode.executor, barcode) }
            histogramChoice != null -> cameraAnalysis(320, 240).also { it.setAnalyzer(CameraHistogram.executor, histogramChoice) }
            else -> null
        }
        preview.setSurfaceProvider(view.surfaceProvider)
        val label = "mode=${mode ?: "standard"},highResolution=$highResolution,rotation=$rotation,motion=${quality ?: "off"}"
        var attached: CameraBinding? = null
        var effect: CameraPreviewEffect? = null
        var lease: CameraSessionLease? = null
        try {
            effect = if (!skipPreviewEffect && (photoMode || videoFilter)) CameraPreviewEffect(previewColors, videoFilter, previewFailure) else null
            val session = if (mode != null) ExtensionSessionConfig.Builder(mode, requireNotNull(manager))
                .addUseCase(preview).addUseCase(requireNotNull(capture)).setViewPort(viewport).apply { effect?.let(::addEffect) }.build()
            else SessionConfig(listOfNotNull(preview, capture, video, analysis), viewport, listOfNotNull(effect))
            // The SessionConfig overload must be paired with unbind(the exact same instance).
            // unbind(vararg UseCase) creates a LegacySessionConfig and is ignored for these sessions.
            val owned = CameraSessionLease(session) { provider.unbind(it) }; lease = owned
            Log.d(CAMERA_LOG_TAG, "Binding $label")
            val camera = provider.bindToLifecycle(owner, selector, session)
            attached = CameraBinding(camera, preview, capture, owned,
                CameraProcessing.entries.firstOrNull { it.extensionMode == mode } ?: CameraProcessing.STANDARD, video, effect, analysis, histogramChoice)
            return finishCameraStartup(attached, { it.release() }) {
                awaitCameraReady(camera.cameraInfo.cameraState, view.previewStreamState)
                Log.d(CAMERA_LOG_TAG, "OPEN and STREAMING $label")
            }
        } catch (cancel: CancellationException) {
            try { analysis?.clearAnalyzer(); lease?.close() } finally { preview.setSurfaceProvider(null); effect?.close() }
            Log.d(CAMERA_LOG_TAG, "Binding cancelled; session released $label")
            throw cancel
        } catch (error: Exception) {
            try { analysis?.clearAnalyzer(); lease?.close() } finally { preview.setSurfaceProvider(null); effect?.close() }; failure = error
            Log.w(CAMERA_LOG_TAG, "Binding failed; session released $label", error)
            // A runtime OEM failure is not fixed by trying every OEM mode with the same device.
            if (mode != null && error is CameraStartupException) skipExtensions = true
            if (motion && !videoMode && quality != null && error is CameraStartupException) skipVideo = true
            if (effect?.failure != null) skipPreviewEffect = true
            attached?.let { previous ->
                withTimeoutOrNull(2_000) { awaitCameraClosed(previous.camera.cameraInfo.cameraState) }
            }
        }
    }
    throw failure ?: IllegalStateException("没有可用相机")
}

internal const val CAMERA_LOG_TAG = "KigPolaroidCamera"

private fun cameraAnalysis(width: Int, height: Int) = ImageAnalysis.Builder()
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy(
        android.util.Size(width, height), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build()).build()

/** Provider/extension futures are process-wide; cancellation must not cancel their shared initialization. */
internal suspend fun <T> ListenableFuture<T>.awaitCamera(context: Context): T = suspendCancellableCoroutine { continuation ->
    addListener({
        if (continuation.isActive) try { continuation.resume(get()) } catch (error: Exception) { continuation.resumeWithException(error) }
    }, ContextCompat.getMainExecutor(context))
}
