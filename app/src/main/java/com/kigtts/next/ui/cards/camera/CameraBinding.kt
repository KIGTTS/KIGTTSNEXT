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
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class CameraBinding(val camera: Camera, val preview: Preview, val capture: ImageCapture,
    val lease: CameraSessionLease, val processing: CameraProcessing = CameraProcessing.STANDARD) {
    fun release() { lease.close(); preview.setSurfaceProvider(null) }
}

@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
internal suspend fun bindPolaroidCamera(context: Context, provider: ProcessCameraProvider, owner: LifecycleOwner,
    selector: CameraSelector, view: PreviewView, rotation: Int, processing: CameraProcessing = CameraProcessing.STANDARD,
    metadata: CameraCaptureSession.CaptureCallback? = null): CameraBinding {
    val viewport = requireNotNull(view.viewPort) { "相机预览尚未就绪" }
    // Ordinary capture does not initialize a vendor extension or wait for its metadata discovery.
    val manager = if (processing == CameraProcessing.STANDARD) null else try { ExtensionsManager.getInstanceAsync(context, provider).awaitCamera(context) }
        catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { null }
    val available = processing.extensionMode?.let { mode -> runCatching { manager?.isExtensionAvailable(selector, mode) == true }.getOrDefault(false) } ?: false
    val modes = cameraExtensionPlan(processing, available)
    var failure: Exception? = null
    var skipExtensions = false
    // Preserve high-resolution/quality preference. Only the explicitly selected OEM mode may precede normal fallback.
    for (mode in modes) for (highResolution in listOf(true, false)) {
        currentCoroutineContext().ensureActive()
        if (mode != null && skipExtensions) continue
        // A previous OPEN/STREAMING value must never make a new session look ready before its first frame.
        awaitCameraPreviewIdle(view.previewStreamState)
        val previewBuilder = Preview.Builder().setTargetRotation(rotation)
        if (mode == null && metadata != null) Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(metadata)
        val preview = previewBuilder.build()
        val resolution = ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
            .setAllowedResolutionMode(if (highResolution) ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE else ResolutionSelector.PREFER_CAPTURE_RATE_OVER_HIGHER_RESOLUTION)
            .setResolutionFilter { sizes, _ -> sizes.filter { it.width.toLong() * it.height <= PhotoFilterWriter.MAX_PIXELS } }.build()
        val capture = ImageCapture.Builder().setTargetRotation(rotation).setJpegQuality(98)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).setResolutionSelector(resolution).build()
        preview.setSurfaceProvider(view.surfaceProvider)
        val session = if (mode != null) ExtensionSessionConfig.Builder(mode, requireNotNull(manager))
            .addUseCase(preview).addUseCase(capture).setViewPort(viewport).build()
        else SessionConfig(listOf(preview, capture), viewport)
        // The SessionConfig overload must be paired with unbind(the exact same instance).
        // unbind(vararg UseCase) creates a LegacySessionConfig and is ignored for these sessions.
        val lease = CameraSessionLease(session) { provider.unbind(it) }
        val label = "mode=${mode ?: "standard"},highResolution=$highResolution,rotation=$rotation"
        var attached: CameraBinding? = null
        try {
            Log.d(CAMERA_LOG_TAG, "Binding $label")
            val camera = provider.bindToLifecycle(owner, selector, session)
            attached = CameraBinding(camera, preview, capture, lease,
                CameraProcessing.entries.firstOrNull { it.extensionMode == mode } ?: CameraProcessing.STANDARD)
            return finishCameraStartup(attached, { it.release() }) {
                awaitCameraReady(camera.cameraInfo.cameraState, view.previewStreamState)
                Log.d(CAMERA_LOG_TAG, "OPEN and STREAMING $label")
            }
        } catch (cancel: CancellationException) {
            lease.close(); preview.setSurfaceProvider(null)
            Log.d(CAMERA_LOG_TAG, "Binding cancelled; session released $label")
            throw cancel
        } catch (error: Exception) {
            lease.close(); preview.setSurfaceProvider(null); failure = error
            Log.w(CAMERA_LOG_TAG, "Binding failed; session released $label", error)
            // A runtime OEM failure is not fixed by trying every OEM mode with the same device.
            if (mode != null && error is CameraStartupException) skipExtensions = true
            attached?.let { previous ->
                withTimeoutOrNull(2_000) { awaitCameraClosed(previous.camera.cameraInfo.cameraState) }
            }
        }
    }
    throw failure ?: IllegalStateException("没有可用相机")
}

internal const val CAMERA_LOG_TAG = "KigPolaroidCamera"

/** Provider/extension futures are process-wide; cancellation must not cancel their shared initialization. */
internal suspend fun <T> ListenableFuture<T>.awaitCamera(context: Context): T = suspendCancellableCoroutine { continuation ->
    addListener({
        if (continuation.isActive) try { continuation.resume(get()) } catch (error: Exception) { continuation.resumeWithException(error) }
    }, ContextCompat.getMainExecutor(context))
}
