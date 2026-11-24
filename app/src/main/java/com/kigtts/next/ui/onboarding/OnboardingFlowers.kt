package com.kigtts.next.ui.onboarding

import android.animation.ValueAnimator
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.kigtts.next.i18n.uiText
import com.kigtts.next.identity.AvatarFrames
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

/** Keep the sensor and its draw-phase state local to the guide. */
@Composable
internal fun rememberOnboardingTilt(enabled: Boolean): State<Offset> {
    val context = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val rotation = remember(view, configuration) { view.display?.rotation ?: Surface.ROTATION_0 }
    val tilt = remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(context, owner, enabled, rotation) {
        if (!enabled) return@LaunchedEffect
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return@LaunchedEffect
        val sensor = manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val listener = object : SensorEventListener {
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                override fun onSensorChanged(event: SensorEvent) {
                    val x = event.values[0] / SensorManager.GRAVITY_EARTH
                    val y = event.values[1] / SensorManager.GRAVITY_EARTH
                    val offset = when (rotation) {
                        Surface.ROTATION_90 -> Offset(-y, x)
                        Surface.ROTATION_180 -> Offset(-x, -y)
                        Surface.ROTATION_270 -> Offset(y, -x)
                        else -> Offset(x, y)
                    }
                    tilt.value = lerp(tilt.value, Offset(offset.x.coerceIn(-1f, 1f), offset.y.coerceIn(-1f, 1f)), .18f)
                }
            }
            if (manager.registerListener(listener, sensor, 33_000)) {
                try { awaitCancellation() }
                finally { manager.unregisterListener(listener); tilt.value = Offset.Zero }
            }
        }
    }
    return tilt
}

@Composable
internal fun BoxScope.OnboardingFlowers(reducedMotion: Boolean, tilt: State<Offset>) {
    val motion = !reducedMotion && ValueAnimator.areAnimatorsEnabled()
    val colors = MaterialTheme.colorScheme
    Flower("flower", 190.dp, .9f, 16f, colors.primaryContainer.copy(alpha = .76f), tilt, motion,
        Modifier.align(Alignment.TopEnd).offset(x = 72.dp, y = (-44).dp))
    Flower("clover", 116.dp, .55f, -23f, colors.tertiaryContainer.copy(alpha = .7f), tilt, motion,
        Modifier.align(Alignment.TopStart).offset(x = (-38).dp, y = 98.dp))
    Flower("cookie", 212.dp, .75f, -12f, colors.primaryContainer.copy(alpha = .36f), tilt, motion,
        Modifier.align(Alignment.CenterStart).offset(x = (-120).dp, y = 36.dp))
    Flower("soft-burst", 164.dp, 1.15f, 9f, colors.secondaryContainer.copy(alpha = .65f), tilt, motion,
        Modifier.align(Alignment.BottomEnd).offset(x = 65.dp, y = (-18).dp))
}

@Composable
private fun Flower(id: String, diameter: Dp, depth: Float, angle: Float, fill: Color, tilt: State<Offset>,
    motion: Boolean, modifier: Modifier) {
    val shape = remember(id) { AvatarFrames.shape(id) }
    val pulse = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var pressed by remember { mutableStateOf(false) }
    Box(modifier.size(diameter).graphicsLayer {
        val gravity = tilt.value
        val ripple = if (motion) pulse.value else 0f
        translationX = 22.dp.toPx() * depth * gravity.x
        translationY = 16.dp.toPx() * depth * gravity.y
        rotationZ = angle + gravity.x * 7f * depth + ripple * 11f
        scaleX = 1f - ripple * .1f
        scaleY = scaleX
    }.clip(shape).background(if (pressed) fill.copy(alpha = (.2f + fill.alpha).coerceAtMost(1f)) else fill)
        .clickable(role = Role.Button, onClickLabel = uiText("onboarding.flower")) {
            pressed = !pressed
            if (motion) {
                job?.cancel()
                job = scope.launch {
                    pulse.animateTo(1f, tween(130))
                    pulse.animateTo(0f, tween(300))
                }
            }
        })
}
