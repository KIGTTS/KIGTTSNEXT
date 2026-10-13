package com.kigtts.next.ui.sketch

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal fun Context.sketchActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext !== this) baseContext.sketchActivity() else null
    else -> null
}

/** A retained drawing page must restore the window as soon as it becomes inactive. */
@Composable internal fun SketchFullscreen(active: Boolean) {
    val activity = LocalContext.current.sketchActivity(); val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(activity, lifecycle, active) {
        if (!active || activity == null) return@DisposableEffect onDispose { }
        val window = activity.window; val decor = window.decorView
        val controller = WindowCompat.getInsetsController(window, decor)
        val initial = ViewCompat.getRootWindowInsets(decor)
        val status = initial?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
        val navigation = initial?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
        val behavior = controller.systemBarsBehavior
        fun hide() {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        hide()
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) hide() }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer); controller.systemBarsBehavior = behavior
            if (status) controller.show(WindowInsetsCompat.Type.statusBars()) else controller.hide(WindowInsetsCompat.Type.statusBars())
            if (navigation) controller.show(WindowInsetsCompat.Type.navigationBars()) else controller.hide(WindowInsetsCompat.Type.navigationBars())
        }
    }
}
