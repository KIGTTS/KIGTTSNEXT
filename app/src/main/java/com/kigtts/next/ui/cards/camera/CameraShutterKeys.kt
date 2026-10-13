package com.kigtts.next.ui.cards.camera

import android.content.Context
import android.content.ContextWrapper
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/** The camera registers for hardware keys only while its page is active. */
interface CameraShutterKeyOwner { val cameraShutterKeys: CameraShutterKeys }

class CameraShutterKeys internal constructor() {
    private class Registration(val shutter: () -> Unit)
    private var binding: Registration? = null
    private val pressed = mutableSetOf<Int>()

    internal fun register(action: () -> Unit): AutoCloseable {
        val registration = Registration(action)
        binding = registration
        return AutoCloseable { if (binding === registration) binding = null }
    }

    internal fun dispatch(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP && event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        if (event.action == KeyEvent.ACTION_UP) return pressed.remove(event.keyCode)
        if (event.action != KeyEvent.ACTION_DOWN) return false
        val action = binding?.shutter ?: return false
        pressed += event.keyCode
        if (event.repeatCount == 0 && !event.isCanceled) action()
        return true
    }

    internal fun clearPressed() { pressed.clear() }
}

private tailrec fun Context.cameraKeyOwner(): CameraShutterKeyOwner? = when (this) {
    is CameraShutterKeyOwner -> this
    is ContextWrapper -> if (baseContext === this) null else baseContext.cameraKeyOwner()
    else -> null
}

@Composable
internal fun RegisterCameraShutterKeys(active: Boolean, shutter: () -> Unit) {
    val owner = LocalContext.current.cameraKeyOwner()
    val current = rememberUpdatedState(shutter)
    DisposableEffect(owner, active) {
        val registration = if (active) owner?.cameraShutterKeys?.register { current.value() } else null
        onDispose { registration?.close() }
    }
}
