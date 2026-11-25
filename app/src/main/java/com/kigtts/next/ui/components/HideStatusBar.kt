package com.kigtts.next.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private tailrec fun Context.statusBarActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.takeUnless { it === this }?.statusBarActivity()
    else -> null
}

/** Own the status bar only while the calling surface requests it. */
@Composable
internal fun HideStatusBar(active: Boolean) {
    val activity = LocalContext.current.statusBarActivity()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(activity, lifecycle, active) {
        if (!active || activity == null) return@DisposableEffect onDispose {}
        val window = activity.window
        val decor = window.decorView
        val controller = WindowCompat.getInsetsController(window, decor)
        val bars = WindowInsetsCompat.Type.statusBars()
        val legacyFullscreen = window.attributes.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN != 0
        val wasVisible = if (Build.VERSION.SDK_INT < 30)
            !legacyFullscreen && decor.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0
        else ViewCompat.getRootWindowInsets(decor)?.isVisible(bars) ?: true
        val previousBehavior = controller.systemBarsBehavior
        fun hideStatusBar() {
            // API 29 and older can lose SYSTEM_UI_FLAG_FULLSCREEN on focus/orientation changes.
            if (Build.VERSION.SDK_INT < 30) window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(bars)
        }
        val hideAfterWindowUpdate = Runnable { hideStatusBar() }
        fun scheduleHide() { decor.removeCallbacks(hideAfterWindowUpdate); decor.post(hideAfterWindowUpdate) }
        hideStatusBar()
        scheduleHide()
        val focus = ViewTreeObserver.OnWindowFocusChangeListener { focused -> if (focused) scheduleHide() }
        decor.viewTreeObserver.addOnWindowFocusChangeListener(focus)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) scheduleHide()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            decor.removeCallbacks(hideAfterWindowUpdate)
            if (decor.viewTreeObserver.isAlive) decor.viewTreeObserver.removeOnWindowFocusChangeListener(focus)
            if (Build.VERSION.SDK_INT < 30 && !legacyFullscreen) window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            controller.systemBarsBehavior = previousBehavior
            if (wasVisible) controller.show(bars)
        }
    }
}
