package com.kigtts.next.overlay

import android.app.Activity
import java.lang.ref.WeakReference

/** Tracks only our resumed host, without inspecting other applications or retaining activities. */
internal object OverlayHostWindow {
    private var resumed = WeakReference<Activity>(null)
    fun resume(activity: Activity) { resumed = WeakReference(activity) }
    fun pause(activity: Activity) { if (resumed.get() === activity) resumed.clear() }
    val inForeground: Boolean
        get() = resumed.get()?.let { !it.isFinishing && !it.isDestroyed } == true
    val fullscreenInForeground: Boolean
        get() = resumed.get()?.let { !it.isFinishing && !it.isDestroyed && !it.isInMultiWindowMode } == true
}
