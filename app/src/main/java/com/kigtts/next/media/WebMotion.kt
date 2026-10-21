package com.kigtts.next.media

import android.content.Context
import android.hardware.*
import android.webkit.WebView
import org.json.JSONObject

internal class WebMotion(private val web: WebView, private val active: () -> Boolean) : SensorEventListener {
    private val sensors = web.context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var running = false
    fun start(): Boolean {
        if (running) return true
        val acceleration = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return false
        running = sensors.registerListener(this, acceleration, 100000)
        sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { sensors.registerListener(this, it, 100000) }
        return running
    }
    fun stop() { sensors.unregisterListener(this); running = false }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onSensorChanged(event: SensorEvent) {
        if (!active()) { stop(); return }
        val data = JSONObject()
        val type = if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            data.put("accelerationIncludingGravity", JSONObject().put("x", event.values[0]).put("y", event.values[1]).put("z", event.values[2])).put("acceleration", JSONObject.NULL).put("rotationRate", JSONObject.NULL).put("interval", 100)
            "devicemotion"
        } else {
            val matrix = FloatArray(9); val angles = FloatArray(3)
            SensorManager.getRotationMatrixFromVector(matrix, event.values); SensorManager.getOrientation(matrix, angles)
            data.put("alpha", Math.toDegrees(angles[0].toDouble())).put("beta", Math.toDegrees(angles[1].toDouble())).put("gamma", Math.toDegrees(angles[2].toDouble())).put("absolute", false)
            "deviceorientation"
        }
        web.evaluateJavascript("(()=>{const d=$data,e=new Event('$type');for(const k of Object.keys(d))Object.defineProperty(e,k,{value:d[k]});window.dispatchEvent(e);})()", null)
    }
}
