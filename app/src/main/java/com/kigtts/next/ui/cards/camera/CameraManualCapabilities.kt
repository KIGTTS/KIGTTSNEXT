package com.kigtts.next.ui.cards.camera

import android.content.Context
import android.hardware.camera2.*
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToLong

internal data class CameraManualCapabilities(val exposure: LongRange? = null, val sensitivity: IntRange? = null,
    val maximumFocus: Float = 0f, val maximumFrameDuration: Long? = null, val frameDurationWritable: Boolean = false,
    val reason: String? = null, val whiteBalance: List<CameraWhiteBalance> = emptyList()) {
    val shutterAvailable get() = exposure?.let { it.first > 0 && it.last >= it.first } == true &&
        sensitivity?.let { it.first > 0 && it.last >= it.first } == true
    val focusAvailable get() = maximumFocus.isFinite() && maximumFocus > 0f
}

internal enum class CameraWhiteBalance(val mode: Int, val label: String) {
    AUTO(CaptureRequest.CONTROL_AWB_MODE_AUTO, "自动"),
    DAYLIGHT(CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT, "日光"),
    CLOUDY(CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT, "阴天"),
    SHADE(CaptureRequest.CONTROL_AWB_MODE_SHADE, "阴影"),
    INCANDESCENT(CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT, "白炽灯"),
    FLUORESCENT(CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT, "荧光灯"),
    WARM_FLUORESCENT(CaptureRequest.CONTROL_AWB_MODE_WARM_FLUORESCENT, "暖荧光灯"),
    TWILIGHT(CaptureRequest.CONTROL_AWB_MODE_TWILIGHT, "暮光");
}

@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
internal fun readCameraManualCapabilities(context: Context, camera: Camera, processing: CameraProcessing): CameraManualCapabilities {
    if (processing != CameraProcessing.STANDARD) return CameraManualCapabilities(reason = "camera.manual_standard")
    return runCatching {
        val info = Camera2CameraInfo.from(camera.cameraInfo)
        val characteristics = requireNotNull(context.getSystemService(CameraManager::class.java)).getCameraCharacteristics(info.cameraId)
        val keys = characteristics.availableCaptureRequestKeys.orEmpty().toSet()
        val manualSensor = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) == true
        val exposure = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val sensitivity = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val maxFrame = characteristics.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION)?.takeIf { it > 0 }
        val canExpose = manualSensor && exposure != null && sensitivity != null &&
            keys.containsAll(listOf(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.SENSOR_EXPOSURE_TIME, CaptureRequest.SENSOR_SENSITIVITY))
        val focus = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
        val canFocus = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.contains(CaptureRequest.CONTROL_AF_MODE_OFF) == true &&
            keys.containsAll(listOf(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.LENS_FOCUS_DISTANCE))
        val awbModes = characteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)
        CameraManualCapabilities(if (canExpose) requireNotNull(exposure).let { it.lower..minOf(it.upper, maxFrame ?: it.upper) } else null,
            if (canExpose) requireNotNull(sensitivity).let { it.lower..it.upper } else null,
            if (canFocus) focus else 0f, maxFrame, CaptureRequest.SENSOR_FRAME_DURATION in keys,
            whiteBalance = CameraWhiteBalance.entries.filter {
                CaptureRequest.CONTROL_AWB_MODE in keys && awbModes?.contains(it.mode) == true
            })
    }.getOrElse { CameraManualCapabilities(reason = "camera.manual_unavailable") }
}

internal fun shutterStops(range: LongRange): List<Long> = listOf(1_000_000L, 2_000_000L, 4_000_000L, 8_000_000L,
    16_666_667L, 33_333_333L, 66_666_667L, 125_000_000L, 250_000_000L, 500_000_000L,
    1_000_000_000L, 2_000_000_000L, 4_000_000_000L, 8_000_000_000L).filter { it in range }

internal fun shutterFraction(nanos: Long, range: LongRange): Float =
    if (range.last <= range.first) 0f else ((ln(nanos.coerceIn(range).toDouble()) - ln(range.first.toDouble())) /
        (ln(range.last.toDouble()) - ln(range.first.toDouble()))).toFloat()

internal fun shutterAt(fraction: Float, range: LongRange): Long =
    exp(ln(range.first.toDouble()) + fraction.coerceIn(0f, 1f) * (ln(range.last.toDouble()) - ln(range.first.toDouble())))
        .roundToLong().coerceIn(range)

internal fun shutterDisplayValue(nanos: Long): String = if (nanos >= 1_000_000_000L)
    "%.1f".format(java.util.Locale.ROOT, nanos / 1_000_000_000.0)
else "1/" + (1_000_000_000.0 / nanos).roundToLong().coerceAtLeast(1)
