package com.kigtts.next.ui.cards.camera

import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs

/** Absolute preview sizes can change when controls appear, without changing the camera crop. */
internal data class CameraViewportKey(val layoutReady: Boolean, val matchesPhotoRatio: Boolean, val rotation: Int)

internal fun cameraViewportKey(size: IntSize, photoRatio: Double, rotation: Int): CameraViewportKey {
    val ready = size.width > 0 && size.height > 0
    val matches = ready && abs(size.width.toDouble() / size.height - photoRatio) < .025
    return CameraViewportKey(ready, matches, rotation)
}

/** Display callbacks also cover 180-degree turns which leave width/height and configuration unchanged. */
@Composable
internal fun rememberCameraDisplayRotation(view: View): Int {
    val root = LocalView.current
    var rotation by remember(view, root) { mutableIntStateOf(view.display?.rotation ?: root.display?.rotation ?: Surface.ROTATION_0) }
    DisposableEffect(view) {
        val manager = view.context.getSystemService(DisplayManager::class.java)
        fun update() { view.display?.rotation?.let { rotation = it } }
        val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) { update() }
            override fun onDisplayRemoved(displayId: Int) { update() }
            override fun onDisplayChanged(displayId: Int) {
                if (displayId == view.display?.displayId) update()
            }
        }
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) { update() }
            override fun onViewDetachedFromWindow(view: View) = Unit
        }
        view.addOnAttachStateChangeListener(attachListener)
        manager?.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        update()
        onDispose {
            view.removeOnAttachStateChangeListener(attachListener)
            manager?.unregisterDisplayListener(displayListener)
        }
    }
    return rotation
}
