package com.kigtts.next.ui.cards.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.RggbChannelVector
import android.os.Build
import androidx.annotation.RequiresApi

internal const val CAMERA_WB_DAYLIGHT_KELVIN = 5500

internal fun cameraKelvinLabel(temperature: Int) = "$temperature K"

internal sealed interface CameraTemperatureControl {
    val range: IntRange

    data class Native(override val range: IntRange) : CameraTemperatureControl

    class Calibrated(private val first: SensorColorCalibration, private val second: SensorColorCalibration?) : CameraTemperatureControl {
        override val range = 2500..10000

        fun gainsAt(temperature: Int): RggbChannelVector? {
            val kelvin = temperature.coerceIn(range)
            val white = planckianWhitePoint(kelvin)
            val fraction = if (second != null && first.kelvin != second.kelvin)
                ((1.0 / kelvin - 1.0 / first.kelvin) / (1.0 / second.kelvin - 1.0 / first.kelvin)).coerceIn(0.0, 1.0)
                else 0.0
            val signal = DoubleArray(3) { channel ->
                val low = first.response(channel, white)
                val high = second?.response(channel, white) ?: low
                low + (high - low) * fraction
            }
            if (signal.any { !it.isFinite() || it <= 0.0 }) return null
            val strongest = signal.max()
            // Camera2 guarantees gains from 1 through 3 across devices with this control.
            fun gain(channel: Int) = (strongest / signal[channel]).toFloat().coerceIn(1f, 3f)
            return RggbChannelVector(gain(0), gain(1), gain(1), gain(2))
        }
    }
}

internal class SensorColorCalibration(val kelvin: Int, private val xyzToSensor: DoubleArray) {
    fun response(channel: Int, white: DoubleArray): Double =
        white.indices.sumOf { column -> xyzToSensor[channel * 3 + column] * white[column] }
}

internal fun readCameraTemperatureControl(characteristics: CameraCharacteristics, modes: IntArray?,
    keys: Set<CaptureRequest.Key<*>>): CameraTemperatureControl? {
    if (modes?.contains(CaptureRequest.CONTROL_AWB_MODE_OFF) != true ||
        CaptureRequest.CONTROL_AWB_MODE !in keys || CaptureRequest.COLOR_CORRECTION_MODE !in keys) return null
    if (Build.VERSION.SDK_INT >= 36) readNativeTemperature(characteristics, keys)?.let { return it }
    if (characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING) != true ||
        CaptureRequest.COLOR_CORRECTION_GAINS !in keys || CaptureRequest.COLOR_CORRECTION_TRANSFORM !in keys) return null

    val first = sensorCalibration(characteristics.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1),
        characteristics.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1),
        characteristics.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM1))
    val second = sensorCalibration(characteristics.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2)?.toInt(),
        characteristics.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM2),
        characteristics.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM2))
    val calibrated = CameraTemperatureControl.Calibrated(first ?: second ?: return null, second.takeIf { first != null })
    return calibrated.takeIf { control ->
        (control.range.first..control.range.last step 500).all { control.gainsAt(it) != null }
    }
}

@RequiresApi(36)
private fun readNativeTemperature(characteristics: CameraCharacteristics,
    keys: Set<CaptureRequest.Key<*>>): CameraTemperatureControl.Native? {
    if (CaptureRequest.COLOR_CORRECTION_COLOR_TEMPERATURE !in keys ||
        characteristics.get(CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_MODES)
            ?.contains(CameraMetadata.COLOR_CORRECTION_MODE_CCT) != true) return null
    val range = characteristics.get(CameraCharacteristics.COLOR_CORRECTION_COLOR_TEMPERATURE_RANGE) ?: return null
    return if (range.lower > 0 && range.upper > range.lower) CameraTemperatureControl.Native(range.lower..range.upper) else null
}

private fun sensorCalibration(illuminant: Int?, color: ColorSpaceTransform?, calibration: ColorSpaceTransform?): SensorColorCalibration? {
    if (color == null) return null
    val kelvin = when (illuminant) {
        CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_TUNGSTEN, CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_STANDARD_A -> 2856
        CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_ISO_STUDIO_TUNGSTEN -> 3200
        CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_D50 -> 5003
        CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT, CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_FINE_WEATHER,
        CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_FLASH, CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_D55 -> 5503
        CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_CLOUDY_WEATHER -> 6000
        CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_D65 -> 6504
        CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_D75 -> 7504
        CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_SHADE -> 8000
        else -> return null
    }
    val matrix = DoubleArray(9) { index ->
        val row = index / 3
        val column = index % 3
        if (calibration == null) color.getElement(column, row).toDouble()
        else (0..2).sumOf { calibration.getElement(it, row).toDouble() * color.getElement(column, it).toDouble() }
    }
    return SensorColorCalibration(kelvin, matrix)
}

/** Planckian locus fit for the calibrated range (Kang et al., JKPS 41, 865–871, 2002). */
private fun planckianWhitePoint(kelvin: Int): DoubleArray {
    val t = 1000.0 / kelvin
    val x = if (kelvin <= 4000) ((-.2661239 * t - .2343589) * t + .8776956) * t + .179910
        else ((-3.0258469 * t + 2.1070379) * t + .2226347) * t + .240390
    val y = if (kelvin <= 4000) ((-.9549476 * x - 1.37418593) * x + 2.09137015) * x - .16748867
        else ((3.0817580 * x - 5.8733867) * x + 3.75112997) * x - .37001483
    return doubleArrayOf(x / y, 1.0, (1.0 - x - y) / y)
}
