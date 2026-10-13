package com.kigtts.next.ui.cards.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner

/** No listener survives the camera page/background; unavailable sensors produce no false reading. */
@Composable
internal fun rememberCameraLevel(rotation: Int, active: Boolean): State<CameraLevelReading?> {
    val context = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    val reading = remember { mutableStateOf<CameraLevelReading?>(null) }
    DisposableEffect(context, owner, rotation, active) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var registered = false
        var lastTimestamp = 0L
        var filtered: FloatArray? = null
        reading.value = null
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            override fun onSensorChanged(event: SensorEvent) {
                if (!registered || event.values.size < 3) return
                val values = if (sensor?.type == Sensor.TYPE_ACCELEROMETER) {
                    val previous = filtered ?: event.values.copyOfRange(0, 3).also { filtered = it }
                    for (axis in 0..2) previous[axis] += .16f * (event.values[axis] - previous[axis])
                    previous
                } else event.values
                // Draw-phase state limits updates to 30 Hz without remeasuring the camera layout.
                if (event.timestamp - lastTimestamp < 33_000_000L) return
                lastTimestamp = event.timestamp
                reading.value = cameraLevelReading(values[0], values[1], values[2], rotation)
            }
        }
        fun stop() {
            if (registered) manager?.unregisterListener(listener)
            registered = false; filtered = null; lastTimestamp = 0L; reading.value = null
        }
        fun start() {
            if (!active || registered || manager == null || sensor == null) return
            registered = manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = start()
            override fun onStop(owner: LifecycleOwner) = stop()
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        onDispose { owner.lifecycle.removeObserver(observer); stop() }
    }
    return reading
}
