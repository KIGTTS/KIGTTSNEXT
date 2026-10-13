package com.kigtts.next.ui.cards.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.core.content.ContextCompat
import com.kigtts.plugin.api.PolaroidFormat
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID
import android.view.View
import kotlin.coroutines.resume

/** Only this leaf's use cases are unbound; leaving/backgrounding cannot deliver a late capture. */
internal class PolaroidCameraSession(private val context: Context, private val owner: LifecycleOwner,
    private val scope: CoroutineScope, private val message: (String) -> Unit) {
    var loading by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var ready by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var canFlip by mutableStateOf(false); private set
    var focusTarget by mutableStateOf<CameraFocusTarget?>(null); private set
    var exposureIndex by mutableIntStateOf(0); private set
    var exposureRange by mutableStateOf(0..0); private set
    var zoomRatio by mutableFloatStateOf(1f); private set
    private var binding: CameraBinding? = null
    private var captureJob: Job? = null
    private var observer: Observer<CameraState>? = null
    private var zoomObserver: Observer<ZoomState>? = null
    private var focusJob: Job? = null
    private var focusVersion = 0L
    private var exposureVersion = 0L
    private var focusResetPending = false
    private var generation = 0L
    private var boundFront = false
    private var delivering = false
    suspend fun bind(view: PreviewView, front: Boolean, rotation: Int) {
        if (delivering) return
        val previous = binding
        unbind(); val epoch = generation; loading = true; error = null
        try {
            if (previous != null) {
                val closed = withTimeoutOrNull(2_500) { awaitCameraClosed(previous.camera.cameraInfo.cameraState); true } ?: false
                if (!closed) throw CameraStartupException("Previous camera session has not closed")
            }
            view.awaitPreviewLayout()
            val cameras = ProcessCameraProvider.getInstance(context).awaitCamera(context)
            currentCoroutineContext().ensureActive()
            val back = cameras.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
            val selfie = cameras.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA); canFlip = back && selfie
            val selector = if (front && selfie || !back && selfie) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            boundFront = selector == CameraSelector.DEFAULT_FRONT_CAMERA
            require(cameras.hasCamera(selector)) { "没有可用相机" }
            val attached = bindPolaroidCamera(context, cameras, owner, selector, view, rotation)
            if (epoch != generation) { attached.release(); return }
            // Adopt before checking cancellation: catch/unbind can then release the newly created session.
            binding = attached
            currentCoroutineContext().ensureActive()
            val exposure = attached.camera.cameraInfo.exposureState
            exposureRange = if (exposure.isExposureCompensationSupported)
                exposure.exposureCompensationRange.let { it.lower..it.upper } else 0..0
            exposureIndex = exposure.exposureCompensationIndex
            zoomObserver = Observer<ZoomState> { state -> if (generation == epoch) zoomRatio = state.zoomRatio }
                .also { attached.camera.cameraInfo.zoomState.observe(owner, it) }
            observer = Observer<CameraState> { state ->
                if (generation == epoch) {
                    ready = state.type == CameraState.Type.OPEN
                    if (ready) error = null
                    state.error?.let { failure ->
                        error = if (failure.code == CameraState.ERROR_CAMERA_IN_USE || failure.code == CameraState.ERROR_MAX_CAMERAS_IN_USE)
                            "相机正被其它应用使用" else "相机暂时不可用，请重试"
                    }
                }
            }.also { attached.camera.cameraInfo.cameraState.observe(owner, it) }
        } catch (cancel: CancellationException) { if (epoch == generation) unbind(); throw cancel }
        catch (failure: Exception) { if (epoch == generation) {
            unbind(); error = "相机暂时无法打开，请重试"
            Log.w(CAMERA_LOG_TAG, "Camera leaf startup failed", failure)
        } }
        finally { if (epoch == generation) loading = false }
    }
    fun focus(view: PreviewView, x: Float, y: Float) {
        val camera = binding?.camera ?: return
        if (!ready || busy) return
        clearFocus()
        focusTarget = CameraFocusTarget(x.coerceIn(0f, view.width.toFloat()), y.coerceIn(0f, view.height.toFloat()))
        val target = requireNotNull(focusTarget); val version = focusVersion
        refreshFocusTimeout()
        try {
            val future = camera.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(
                view.meteringPointFactory.createPoint(target.x, target.y)).disableAutoCancel().build())
            future.addListener({
                if (version == focusVersion && binding?.camera === camera) {
                    val success = runCatching { future.get().isFocusSuccessful }.getOrDefault(false)
                    if (!success) resetFailedFocus()
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (failure: Exception) { resetFailedFocus(); Log.w(CAMERA_LOG_TAG, "Manual focus failed", failure) }
    }
    fun setExposure(index: Int) {
        val camera = binding?.camera ?: return
        if (!ready || busy || focusTarget == null || exposureRange == 0..0) return
        val selected = index.coerceIn(exposureRange); val request = ++exposureVersion; val focus = focusVersion
        exposureIndex = selected; refreshFocusTimeout()
        try {
            val future = camera.cameraControl.setExposureCompensationIndex(selected)
            future.addListener({
                if (request == exposureVersion && focus == focusVersion && binding?.camera === camera && runCatching { future.get() }.isFailure)
                    resetFailedFocus()
            }, ContextCompat.getMainExecutor(context))
        } catch (failure: Exception) { resetFailedFocus(); Log.w(CAMERA_LOG_TAG, "Exposure adjustment failed", failure) }
    }
    fun zoomBy(scale: Float) {
        val camera = binding?.camera ?: return
        if (!ready || busy || !scale.isFinite() || scale <= 0f) return
        val zoom = camera.cameraInfo.zoomState.value ?: return
        val requested = (zoomRatio * scale).coerceIn(zoom.minZoomRatio, zoom.maxZoomRatio)
        runCatching { camera.cameraControl.setZoomRatio(requested); zoomRatio = requested }
            .onFailure { Log.w(CAMERA_LOG_TAG, "Zoom adjustment failed", it) }
    }
    fun clearFocus() {
        focusVersion++; exposureVersion++; focusJob?.cancel(); focusJob = null
        focusTarget = null; exposureIndex = 0; focusResetPending = false
        binding?.camera?.let { camera ->
            runCatching { camera.cameraControl.cancelFocusAndMetering() }
            if (exposureRange != 0..0) runCatching { camera.cameraControl.setExposureCompensationIndex(0) }
        }
    }
    private fun refreshFocusTimeout() {
        focusJob?.cancel()
        focusJob = scope.launch { delay(5_000); clearFocus() }
    }
    private fun resetFailedFocus() {
        if (busy) focusResetPending = true else clearFocus()
    }
    fun capture(format: PolaroidFormat, filter: PhotoFilter, complete: (PolaroidCameraCapture) -> Unit) {
        val camera = binding?.capture ?: return
        if (!ready || busy) return
        busy = true; val epoch = generation
        // Do not restore compensation while HDR/night is still exposing or processing the photograph.
        focusJob?.cancel(); focusJob = null
        val front = boundFront
        captureJob = scope.launch {
            val directory = File(context.cacheDir, "polaroid-camera").apply { mkdirs() }
            val id = UUID.randomUUID().toString()
            val original = File(directory, "$id-original.jpg"); val processed = File(directory, "$id-filtered.jpg")
            var delivered = false
            try {
                camera.captureJpeg(context, original, front)
                val filtered = try { withContext(Dispatchers.Default) { PhotoFilterWriter.write(original, processed, filter) } }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: OutOfMemoryError) { message("原图已保留，本次未应用滤镜"); null }
                catch (_: Exception) { message("原图已保留，本次未应用滤镜"); null }
                currentCoroutineContext().ensureActive()
                if (epoch == generation) {
                    complete(PolaroidCameraCapture(original, filtered, format, if (filtered == null) PhotoFilter.ORIGINAL else filter))
                    delivered = true; delivering = true
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { if (epoch == generation) message("拍照未完成，请重试") }
            finally {
                if (!delivered) { original.delete(); processed.delete() }
                if (epoch == generation) {
                    busy = delivering
                    if (!delivering) {
                        if (focusResetPending) clearFocus() else if (focusTarget != null) refreshFocusTimeout()
                    }
                }
            }
        }
    }
    fun unbind() {
        generation++; captureJob?.cancel(); captureJob = null
        clearFocus()
        binding?.let { attached -> observer?.let { attached.camera.cameraInfo.cameraState.removeObserver(it) }
            zoomObserver?.let { attached.camera.cameraInfo.zoomState.removeObserver(it) }
            attached.release() }
        observer = null; zoomObserver = null; binding = null; exposureRange = 0..0
        ready = false; loading = false; busy = delivering
    }
}

private suspend fun View.awaitPreviewLayout() = suspendCancellableCoroutine<Unit> { continuation ->
    if (isAttachedToWindow && width > 0 && height > 0) { continuation.resume(Unit); return@suspendCancellableCoroutine }
    val listener = object : View.OnLayoutChangeListener {
        override fun onLayoutChange(view: View, l: Int, t: Int, r: Int, b: Int, oldL: Int, oldT: Int, oldR: Int, oldB: Int) {
            if (view.isAttachedToWindow && view.width > 0 && view.height > 0) {
                view.removeOnLayoutChangeListener(this)
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }
    addOnLayoutChangeListener(listener)
    continuation.invokeOnCancellation { removeOnLayoutChangeListener(listener) }
}
