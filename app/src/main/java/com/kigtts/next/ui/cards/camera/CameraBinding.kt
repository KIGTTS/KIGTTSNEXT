package com.kigtts.next.ui.cards.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.*
import androidx.camera.extensions.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class CameraBinding(val camera: Camera, val preview: Preview, val capture: ImageCapture,
    val lease: CameraSessionLease) {
    fun release() { lease.close(); preview.setSurfaceProvider(null) }
}

internal suspend fun bindPolaroidCamera(context: Context, provider: ProcessCameraProvider, owner: LifecycleOwner,
    selector: CameraSelector, view: PreviewView, rotation: Int): CameraBinding {
    val viewport = requireNotNull(view.viewPort) { "相机预览尚未就绪" }
    val manager = try { ExtensionsManager.getInstanceAsync(context, provider).awaitCamera(context) }
        catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { null }
    val modes = buildList<Int?> {
        listOf(ExtensionMode.AUTO, ExtensionMode.HDR, ExtensionMode.NIGHT).forEach {
            if (runCatching { manager?.isExtensionAvailable(selector, it) == true }.getOrDefault(false)) add(it)
        }
        add(null)
    }
    var failure: Exception? = null
    var skipExtensions = false
    // Prefer OEM processing and high-resolution sizes, then try compatible normal sizes/session.
    for (mode in modes) for (highResolution in listOf(true, false)) {
        currentCoroutineContext().ensureActive()
        if (mode != null && skipExtensions) continue
        // A previous OPEN/STREAMING value must never make a new session look ready before its first frame.
        awaitCameraPreviewIdle(view.previewStreamState)
        val preview = Preview.Builder().setTargetRotation(rotation).build()
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
            attached = CameraBinding(camera, preview, capture, lease)
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
