package com.kigtts.next.ui.cards.camera

import com.kigtts.next.i18n.uiCopy

import android.content.Context
import android.hardware.camera2.*
import androidx.camera.camera2.interop.*
import androidx.camera.core.Camera
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.withTimeoutOrNull

internal data class CameraMeteringSample(val exposure: Long?, val iso: Int?, val frame: Long?, val focus: Float?)

/** One owner for manual Camera2 overrides. Metadata is atomic and never recomposes the UI on each frame. */
@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
internal class CameraManualController(private val context: Context, private val message: (String) -> Unit,
    private val beforeChange: () -> Unit = {}, private val executor: Executor = ContextCompat.getMainExecutor(context)) {
    var capabilities by mutableStateOf(CameraManualCapabilities()); private set
    var manualFocus by mutableStateOf(false); private set
    var focusDiopters by mutableFloatStateOf(0f); private set
    var shutterNanos by mutableStateOf<Long?>(null); private set
    var sensorIso by mutableIntStateOf(0); private set
    var whiteBalance by mutableStateOf(CameraWhiteBalance.AUTO); private set
    val automaticExposure get() = shutterNanos == null
    private val epoch = AtomicLong()
    private val metadataLock = Any()
    private val sample = AtomicReference<CameraMeteringSample?>(null)
    private var sender: ((CaptureRequestOptions?) -> ListenableFuture<Void?>)? = null
    private var version = 0L
    private var pending: ListenableFuture<Void?>? = null

    fun metadataCallback(): CameraCaptureSession.CaptureCallback {
        val expected = epoch.get()
        return object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                synchronized(metadataLock) { if (epoch.get() == expected) sample.set(CameraMeteringSample(result.get(CaptureResult.SENSOR_EXPOSURE_TIME),
                    result.get(CaptureResult.SENSOR_SENSITIVITY), result.get(CaptureResult.SENSOR_FRAME_DURATION), result.get(CaptureResult.LENS_FOCUS_DISTANCE))) }
            }
        }
    }
    fun bind(camera: Camera, processing: CameraProcessing) {
        val capability = readCameraManualCapabilities(context, camera, processing)
        runCatching { Camera2CameraControl.from(camera.cameraControl) }.fold({ control ->
            attach(capability) { options -> if (options == null) control.clearCaptureRequestOptions() else control.setCaptureRequestOptions(options) }
        }, { attach(CameraManualCapabilities(reason = "camera.manual_unavailable"), null) })
    }
    internal fun attach(value: CameraManualCapabilities, submit: ((CaptureRequestOptions?) -> ListenableFuture<Void?>)?) {
        version++; capabilities = value; sender = submit; pending = null
        resetValues()
    }
    internal fun record(value: CameraMeteringSample) { sample.set(value) }
    fun meteringSample(): CameraMeteringSample? = sample.get()
    fun focusMode(enabled: Boolean, distance: Float? = null) {
        if (enabled && !capabilities.focusAvailable) return
        beforeChange(); manualFocus = enabled
        if (enabled) focusDiopters = (distance ?: sample.get()?.focus)?.takeIf(Float::isFinite)?.coerceIn(0f, capabilities.maximumFocus) ?: 0f
        apply()
    }
    fun focusAt(value: Float) {
        if (!manualFocus || !value.isFinite()) return
        focusDiopters = value.coerceIn(0f, capabilities.maximumFocus); apply()
    }
    fun setManualShutter(enabled: Boolean, time: Long? = null, sensitivity: Int? = null) {
        if (enabled && !capabilities.shutterAvailable) return
        beforeChange()
        if (enabled) {
            val range = requireNotNull(capabilities.exposure)
            val measured = sample.get()
            shutterNanos = (time ?: measured?.exposure ?: 16_666_667L).coerceIn(range)
            val iso = requireNotNull(capabilities.sensitivity)
            sensorIso = (sensitivity ?: measured?.iso ?: (iso.first + (iso.last.toLong() - iso.first) / 2).toInt()).coerceIn(iso)
        } else { shutterNanos = null; sensorIso = 0 }
        apply()
    }
    fun exposeFor(value: Long) {
        val range = capabilities.exposure ?: return
        if (shutterNanos == null) return
        shutterNanos = value.coerceIn(range); apply()
    }
    fun sensitivityAt(value: Int) {
        val range = capabilities.sensitivity ?: return
        if (automaticExposure) return
        sensorIso = value.coerceIn(range); apply()
    }
    fun selectWhiteBalance(value: CameraWhiteBalance) {
        if (value != CameraWhiteBalance.AUTO && value !in capabilities.whiteBalance) return
        if (whiteBalance == value) return
        whiteBalance = value; apply()
    }
    fun reset() {
        beforeChange()
        resetValues()
        apply()
    }
    private fun resetValues() {
        manualFocus = false; focusDiopters = 0f; shutterNanos = null; sensorIso = 0; whiteBalance = CameraWhiteBalance.AUTO
    }
    private fun apply() {
        val send = sender ?: return
        val ticket = ++version; val generation = epoch.get()
        val options = if (!manualFocus && automaticExposure && whiteBalance == CameraWhiteBalance.AUTO) null else CaptureRequestOptions.Builder().apply {
            if (whiteBalance != CameraWhiteBalance.AUTO) {
                setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, whiteBalance.mode)
            }
            if (manualFocus) {
                setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, focusDiopters)
            }
            shutterNanos?.let { exposure ->
                setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, exposure)
                setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, sensorIso)
                if (capabilities.frameDurationWritable) setCaptureRequestOption(CaptureRequest.SENSOR_FRAME_DURATION,
                    maxOf(exposure, sample.get()?.frame ?: 33_333_333L).coerceAtMost(capabilities.maximumFrameDuration ?: Long.MAX_VALUE))
            }
        }.build()
        try {
            val future = send(options)
            pending = future
            future.addListener({
                if (epoch.get() == generation && version == ticket && runCatching { future.get() }.isFailure) failed()
            }, executor)
        } catch (_: Exception) { if (version == ticket) failed() }
    }
    private fun failed() {
        version++; resetValues()
        pending = runCatching { sender?.invoke(null) }.getOrNull()
        message(context.uiCopy("camera.manual_restore"))
    }
    suspend fun awaitConfigured() {
        val future = pending ?: return
        val exposureMillis = ((shutterNanos ?: 0L) / 1_000_000).coerceAtMost(120_000)
        val applied = withTimeoutOrNull(exposureMillis + 2_500) { future.awaitCamera(context); true } ?: false
        if (!applied) { failed(); throw CameraStartupException("Manual request acknowledgement timeout") }
    }
    fun unbind() {
        synchronized(metadataLock) { epoch.incrementAndGet(); sample.set(null) }; version++
        runCatching { sender?.invoke(null) }; sender = null; pending = null
        capabilities = CameraManualCapabilities(); resetValues()
    }
}
