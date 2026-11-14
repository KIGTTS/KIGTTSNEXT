package com.kigtts.next.ui.cards.camera

import android.content.Context
import android.util.Log
import android.os.SystemClock
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.extensions.ExtensionsManager
import androidx.compose.runtime.*
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.core.content.ContextCompat
import com.kigtts.plugin.api.PolaroidFormat
import kotlinx.coroutines.*
import android.view.View
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume

/** Only this leaf's use cases are unbound; leaving/backgrounding cannot deliver a late capture. */
internal class PolaroidCameraSession(private val context: Context, private val owner: LifecycleOwner,
    private val scope: CoroutineScope, private val message: (String) -> Unit) {
    var loading by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var ready by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var canFlip by mutableStateOf(false); private set
    var flashAvailable by mutableStateOf(false); private set
    var flashMode by mutableStateOf(CameraFlash.OFF); private set
    var focusTarget by mutableStateOf<CameraFocusTarget?>(null); private set
    val exposure = CameraExposureCompensation()
    var histogram by mutableStateOf<CameraHistogram?>(null); private set
    private val zoomState = CameraZoomState()
    val zoomRatio get() = zoomState.ratio
    val minZoomRatio get() = zoomState.minimum
    val maxZoomRatio get() = zoomState.maximum
    var availableProcessing by mutableStateOf(setOf(CameraProcessing.STANDARD)); private set
    private var binding: CameraBinding? = null
    private val previewColors = CameraPreviewColors()
    val previewFilterAvailable: Boolean get() = binding?.previewEffect != null
    fun setExposureWarning(enabled: Boolean) { previewColors.exposureWarning = enabled }
    fun setPreviewFilter(values: FloatArray, lut: com.kigtts.next.media.color.CubeLut? = null, strength: Float = 1f, effects: com.kigtts.next.media.color.PhotoEffects = com.kigtts.next.media.color.PhotoEffects.NONE) { previewColors.update(values, lut, strength, effects) }
    private var motionBuffer: CameraMotionBuffer? = null
    val motionAvailable: Boolean get() = motionBuffer?.failed == false
    val motionReady: Boolean get() = motionBuffer?.ready == true
    private var captureJob: Job? = null
    private var exposingPhoto = false
    private var nextExposure: (() -> Unit)? = null
    private var deferredZoom: Float? = null
    private var observer: Observer<CameraState>? = null
    private var zoomObserver: Observer<ZoomState>? = null
    private var capabilitiesJob: Job? = null
    private var focusJob: Job? = null
    private var focusVersion = 0L
    private var exposureVersion = 0L
    private var generation = 0L
    private var boundFront = false
    private var delivering = false
    private var preferredFlash = CameraFlash.OFF
    val video = CameraVideoSession(context, message)
    val scan = CameraScanState(context)
    fun startVideo(audio: Boolean, timeLapse: Boolean, saved: (File, Boolean) -> Unit) = video.start(binding?.motionCapture?.output, audio, timeLapse, saved)
    fun close() { unbind(); scan.close() }
    private val countdown = CameraCountdownState(scope)
    val burst = CameraBurstCapture(scope)
    val manual = CameraManualController(context, message, ::clearFocus)
    val countingDown: Boolean get() = countdown.active
    val countdownSeconds: Int get() = countdown.remainingSeconds
    suspend fun bind(view: PreviewView, front: Boolean, rotation: Int, processing: CameraProcessing = CameraProcessing.STANDARD,
        motion: Boolean = false, mode: CameraMode = CameraMode.PHOTO) {
        if (delivering || countingDown) return
        val previous = binding
        unbind(keepFlashAvailability = true, keepZoomState = true); val epoch = generation; loading = true; error = null
        val started = SystemClock.elapsedRealtime()
        try {
            if (previous != null) {
                val closed = withTimeoutOrNull(2_500) { awaitCameraClosed(previous.camera.cameraInfo.cameraState); true } ?: false
                if (!closed) throw CameraStartupException("Previous camera session has not closed")
                Log.d(CAMERA_LOG_TAG, "Previous session CLOSED after ${SystemClock.elapsedRealtime() - started}ms")
            }
            view.awaitPreviewLayout()
            val cameras = ProcessCameraProvider.getInstance(context).awaitCamera(context)
            currentCoroutineContext().ensureActive()
            val back = cameras.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
            val selfie = cameras.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA); canFlip = back && selfie
            val selector = if (front && selfie || !back && selfie) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            boundFront = selector == CameraSelector.DEFAULT_FRONT_CAMERA
            require(cameras.hasCamera(selector)) { "没有可用相机" }
            val attached = bindPolaroidCamera(context, cameras, owner, selector, view, rotation, processing,
                manual.metadataCallback(), motion, previewColors, videoMode = mode.recordsVideo,
                barcode = scan.analyzer.takeIf { mode == CameraMode.SCAN }, histogram = if (mode == CameraMode.PRO) CameraHistogram() else null) { failure ->
                ContextCompat.getMainExecutor(context).execute {
                    if (epoch == generation && binding != null) {
                        error = "实时滤镜暂时不可用，请重试"
                        Log.w(CAMERA_LOG_TAG, "Live filter unavailable in the current session", failure)
                    }
                }
            }
            if (epoch != generation) { attached.release(); return }
            // Adopt before checking cancellation: catch/unbind can then release the newly created session.
            binding = attached
            histogram = attached.histogram
            scan.bind(view.takeIf { mode == CameraMode.SCAN }, epoch)
            currentCoroutineContext().ensureActive()
            if (mode.takesPhotos && attached.previewEffect == null) message("实时滤镜暂时不可用，仍可正常拍照")
            attached.motionCapture?.takeIf { motion && mode == CameraMode.PHOTO }?.let { video ->
                motionBuffer = CameraMotionBuffer(File(context.cacheDir, "polaroid-camera/motion/${UUID.randomUUID()}"),
                    CameraMotionRecorder(context, video.output), scope, message).also { it.start() }
            }
            if (motion && attached.motionCapture == null) message("此镜头暂不支持实况录制，仍可正常拍照")
            flashAvailable = attached.camera.cameraInfo.hasFlashUnit()
            applyFlash()
            manual.bind(attached.camera, attached.processing)
            val exposureState = attached.camera.cameraInfo.exposureState
            exposure.attach(if (exposureState.isExposureCompensationSupported)
                exposureState.exposureCompensationRange.let { it.lower..it.upper } else 0..0,
                exposureState.exposureCompensationStep.toFloat())
            if (exposure.index != exposureState.exposureCompensationIndex) applyExposure(attached.camera)
            val initialZoom = attached.camera.cameraInfo.zoomState.value
            var restoringZoom = initialZoom != null
            val requestedZoom = initialZoom?.let { zoomState.attach(boundFront, it.minZoomRatio, it.maxZoomRatio) }
            zoomObserver = Observer<ZoomState> { state -> if (generation == epoch) {
                zoomState.observed(state.zoomRatio, state.minZoomRatio, state.maxZoomRatio, restoringZoom)
            } }
                .also { attached.camera.cameraInfo.zoomState.observe(owner, it) }
            if (requestedZoom != null) {
                val restore = attached.camera.cameraControl.setZoomRatio(requestedZoom)
                restore.addListener({ if (generation == epoch) {
                    restoringZoom = false
                    attached.camera.cameraInfo.zoomState.value?.let {
                        zoomState.observed(it.zoomRatio, it.minZoomRatio, it.maxZoomRatio, false)
                    }
                } }, ContextCompat.getMainExecutor(context))
            }
            observer = Observer<CameraState> { state ->
                if (generation == epoch) {
                    ready = state.type == CameraState.Type.OPEN
                    if (ready) error = null
                    state.error?.let { failure ->
                        countdown.cancel()
                        error = if (failure.code == CameraState.ERROR_CAMERA_IN_USE || failure.code == CameraState.ERROR_MAX_CAMERAS_IN_USE)
                            "相机正被其它应用使用" else "相机暂时不可用，请重试"
                    }
                }
            }.also { attached.camera.cameraInfo.cameraState.observe(owner, it) }
            Log.d(CAMERA_LOG_TAG, "Leaf ready after ${SystemClock.elapsedRealtime() - started}ms,processing=$processing,resolution=${attached.capture?.resolutionInfo?.resolution}")
            capabilitiesJob = scope.launch {
                try {
                    // Discover optional vendor choices after normal preview readiness; no forced HDR/night startup.
                    val manager = ExtensionsManager.getInstanceAsync(context, cameras).awaitCamera(context)
                    val supported = CameraProcessing.entries.filter { choice -> choice.extensionMode?.let {
                        runCatching { manager.isExtensionAvailable(selector, it) }.getOrDefault(false)
                    } ?: true }.toSet()
                    if (generation == epoch) availableProcessing = supported
                } catch (cancel: CancellationException) { throw cancel }
                catch (failure: Exception) { Log.d(CAMERA_LOG_TAG, "Optional processing discovery unavailable", failure) }
            }
        } catch (cancel: CancellationException) { if (epoch == generation) unbind(keepFlashAvailability = true, keepZoomState = true); throw cancel }
        catch (failure: Exception) { if (epoch == generation) {
            unbind(); error = "相机暂时无法打开，请重试"
            Log.w(CAMERA_LOG_TAG, "Camera leaf startup failed", failure)
        } }
        finally { if (epoch == generation) loading = false }
    }
    fun focus(view: PreviewView, x: Float, y: Float) {
        val camera = binding?.camera ?: return
        if (!ready || busy || countingDown) return
        clearFocus()
        focusTarget = CameraFocusTarget(x.coerceIn(0f, view.width.toFloat()), y.coerceIn(0f, view.height.toFloat()))
        val target = requireNotNull(focusTarget); val version = focusVersion
        refreshFocusTimeout()
        try {
            val future = camera.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(
                view.meteringPointFactory.createPoint(target.x, target.y),
                (if (manual.manualFocus) 0 else FocusMeteringAction.FLAG_AF) or
                    (if (manual.automaticExposure) FocusMeteringAction.FLAG_AE else 0) or FocusMeteringAction.FLAG_AWB)
                .disableAutoCancel().build())
            future.addListener({
                if (version == focusVersion && binding?.camera === camera) {
                    val success = runCatching { future.get().let { manual.manualFocus || it.isFocusSuccessful } }.getOrDefault(false)
                    if (!success) resetFailedFocus()
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (failure: Exception) { resetFailedFocus(); Log.w(CAMERA_LOG_TAG, "Manual focus failed", failure) }
    }
    fun setExposure(index: Int) {
        val camera = binding?.camera ?: return
        if (!ready || busy || countingDown || !manual.automaticExposure || focusTarget == null || exposure.range == 0..0) return
        exposure.chooseFocusOffset(index)
        refreshFocusTimeout()
        applyExposure(camera)
    }
    fun setGlobalExposure(index: Int) {
        val camera = binding?.camera ?: return
        if (!ready || busy || countingDown || !manual.automaticExposure || exposure.range == 0..0) return
        exposure.chooseBaseline(index)
        if (focusTarget != null) refreshFocusTimeout()
        applyExposure(camera)
    }
    private fun applyExposure(camera: Camera) {
        if (exposure.range == 0..0) return
        val request = ++exposureVersion
        try {
            val future = camera.cameraControl.setExposureCompensationIndex(exposure.index)
            future.addListener({
                if (request == exposureVersion && binding?.camera === camera && runCatching { future.get() }.isFailure)
                    Log.w(CAMERA_LOG_TAG, "Exposure compensation not accepted")
            }, ContextCompat.getMainExecutor(context))
        } catch (failure: Exception) { Log.w(CAMERA_LOG_TAG, "Exposure adjustment failed", failure) }
    }
    fun zoomBy(scale: Float) {
        if (!ready || countingDown || !scale.isFinite() || scale <= 0f) return
        zoomTo(zoomRatio * scale)
    }
    fun zoomTo(ratio: Float) {
        val camera = binding?.camera ?: return
        if (!ready || countingDown || !ratio.isFinite()) return
        val zoom = camera.cameraInfo.zoomState.value ?: return
        val requested = ratio.coerceIn(zoom.minZoomRatio, zoom.maxZoomRatio)
        if (busy) { deferredZoom = requested; zoomState.ratio = requested; return }
        if (focusTarget != null) clearFocus()
        runCatching { camera.cameraControl.setZoomRatio(requested); zoomState.ratio = requested }
            .onFailure { Log.w(CAMERA_LOG_TAG, "Zoom adjustment failed", it) }
    }
    fun clearFocus() {
        val hadFocus = focusTarget != null
        val changedExposure = exposure.focusOffset != 0
        focusVersion++; focusJob?.cancel(); focusJob = null
        focusTarget = null; exposure.clearFocus()
        binding?.camera?.let { camera ->
            if (hadFocus) runCatching { camera.cameraControl.cancelFocusAndMetering() }
            if (changedExposure && manual.automaticExposure) applyExposure(camera)
        }
    }
    private fun refreshFocusTimeout() {
        focusJob?.cancel()
        focusJob = scope.launch { delay(5_000); clearFocus() }
    }
    private fun resetFailedFocus() {
        // Capturing/countdown owns the selected exposure until cancellation, failure or navigation cleans up.
        if (!busy && !countingDown) clearFocus()
    }
    fun setFlash(mode: CameraFlash) {
        if (busy || countingDown) return
        preferredFlash = mode
        applyFlash()
    }
    private fun applyFlash() {
        flashMode = applyCameraFlash(binding?.capture, preferredFlash, flashAvailable)
    }
    fun cancelCountdown(): Boolean = countdown.cancel()
    fun startBurst(format: PolaroidFormat, filter: PhotoFilter, complete: (List<PolaroidCameraCapture>) -> Unit,
        strength: Float = 1f, lut: com.kigtts.next.media.color.CubeLut? = null,
        effects: com.kigtts.next.media.color.PhotoEffects = com.kigtts.next.media.color.PhotoEffects.NONE) {
        val capture = binding?.capture ?: return
        if (!ready || busy || countingDown) return
        val epoch = generation; val front = boundFront
        focusJob?.cancel(); focusJob = null; busy = true
        val started = burst.start(capture = {
            manual.awaitConfigured()
            capturePolaroidPhoto(context, capture, front, format, PhotoFilter.ORIGINAL, message)
        }, process = { processBurstPhoto(it, filter, strength, PhotoFilters.lut(filter, lut), effects, message) },
            hasSpace = { context.cacheDir.usableSpace > 64L * 1024 * 1024 }, message = message,
            complete = { shots ->
                complete(shots); if (epoch == generation) delivering = false
            }, finished = { if (epoch == generation) {
                busy = delivering
                if (!delivering) { clearFocus(); applyDeferredZoom() }
            } })
        if (!started) busy = false
    }
    fun capture(format: PolaroidFormat, filter: PhotoFilter, complete: (PolaroidCameraCapture) -> Unit,
        delaySeconds: Int = 0, strength: Float = 1f, lut: com.kigtts.next.media.color.CubeLut? = null,
        effects: com.kigtts.next.media.color.PhotoEffects = com.kigtts.next.media.color.PhotoEffects.NONE,
        allowNextExposure: Boolean = false) {
        if (!ready || countingDown || delaySeconds !in listOf(0, 3, 10)) return
        if (busy) {
            if (allowNextExposure && delaySeconds == 0 && exposingPhoto && nextExposure == null) {
                nextExposure = { capture(format, filter, complete, delaySeconds, strength, lut, effects, allowNextExposure) }
            }
            return
        }
        val current = binding ?: return
        val epoch = generation
        // Freeze manual focus/EV from the initial shutter tap through the eventual exposure.
        focusJob?.cancel(); focusJob = null
        val started = countdown.start(delaySeconds, {
            if (epoch == generation && binding === current && ready) performCapture(format, filter, complete, strength, PhotoFilters.lut(filter, lut), effects)
            else if (epoch == generation) clearFocus()
        }, { if (epoch == generation) clearFocus() })
        if (!started) clearFocus()
    }
    private fun performCapture(format: PolaroidFormat, filter: PhotoFilter, complete: (PolaroidCameraCapture) -> Unit, strength: Float,
        lut: com.kigtts.next.media.color.CubeLut?, effects: com.kigtts.next.media.color.PhotoEffects) {
        val camera = binding?.capture ?: return
        if (!ready || busy) return
        busy = true; exposingPhoto = true; val epoch = generation
        // Do not restore compensation while HDR/night is still exposing or processing the photograph.
        focusJob?.cancel(); focusJob = null
        val front = boundFront
        val motion = motionBuffer
        captureJob = scope.launch {
            var photo: PolaroidCameraCapture? = null
            var motionTicket: CameraMotionTicket? = null
            var delivered = false
            try {
                manual.awaitConfigured()
                motionTicket = motion?.beginCapture()
                if (motion != null && motionTicket == null && !motion.failed) message("实况还在准备，本次保存照片")
                photo = capturePolaroidPhoto(context, camera, front, format, filter, message, strength, lut, effects) {
                    motionTicket?.let { motion?.exposureStarted(it) }
                }
                val clip = motionTicket?.let { ticket -> motion?.finish(ticket,
                    PhotoFilters.coefficients(requireNotNull(photo).filter, strength), lut, strength, effects) }
                if (clip != null) photo = requireNotNull(photo).withMotion(clip)
                currentCoroutineContext().ensureActive()
                if (epoch == generation) {
                    complete(photo)
                    delivered = true; delivering = false
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { if (epoch == generation) message("拍照未完成，请重试") }
            finally {
                if (!delivered) { motion?.abort(motionTicket); photo?.discard() }
                if (epoch == generation) {
                    exposingPhoto = false
                    busy = delivering
                    val queued = nextExposure
                    nextExposure = null
                    if (!delivering) {
                        motion?.start()
                        applyDeferredZoom()
                        if (queued != null && ready) queued()
                        else clearFocus()
                    }
                }
            }
        }
    }
    private fun applyDeferredZoom() {
        val zoom = deferredZoom ?: return
        deferredZoom = null
        zoomTo(zoom)
    }
    fun unbind(keepFlashAvailability: Boolean = false, keepZoomState: Boolean = false) {
        nextExposure = null; exposingPhoto = false; deferredZoom = null
        video.stop(); scan.bind(null, ++generation); burst.cancel(); captureJob?.cancel(); captureJob = null
        capabilitiesJob?.cancel(); capabilitiesJob = null
        countdown.cancel()
        motionBuffer?.close(); motionBuffer = null
        clearFocus()
        manual.unbind()
        binding?.let { attached -> observer?.let { attached.camera.cameraInfo.cameraState.removeObserver(it) }
            zoomObserver?.let { attached.camera.cameraInfo.zoomState.removeObserver(it) }
            attached.release() }
        observer = null; zoomObserver = null; binding = null; histogram = null; exposureVersion++
        exposure.detach(keepBaseline = true)
        if (!keepFlashAvailability) flashAvailable = false
        flashMode = CameraFlash.OFF
        if (!keepZoomState) zoomState.clear()
        availableProcessing = setOf(CameraProcessing.STANDARD)
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
