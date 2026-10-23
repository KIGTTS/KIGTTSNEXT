package com.kigtts.next.overlay

import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

/** Geometry already applies absolute system-bar/cutout insets; WindowManager must not apply them again. */
internal fun overlayWindowParams() = WindowManager.LayoutParams(80, 80, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, PixelFormat.TRANSLUCENT).apply {
    windowAnimations = 0
    gravity = Gravity.TOP or Gravity.LEFT; title = "KIGTTS 悬浮快捷入口"
    if (Build.VERSION.SDK_INT >= 30) {
        setFitInsetsTypes(0)
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }
}

internal class OverlayWindow(context: Context, private val wm: WindowManager, outside: () -> Unit) {
    private val owner = OverlayViewOwner()
    private val view = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setOnTouchListener { _, event -> if (event.action == MotionEvent.ACTION_OUTSIDE) { outside(); true } else false }
    }
    private val params = overlayWindowParams()
    private var attached = false
    private var settling: ValueAnimator? = null
    init {
        owner.attach(view)
        // Geometry and WindowManager resize arrive through separate frame phases.
        // Do not paint new local FAB coordinates into the old-sized surface.
        view.viewTreeObserver.addOnPreDrawListener {
            !attached || (view.width == params.width && view.height == params.height)
        }
    }
    fun content(content: @Composable () -> Unit) = view.setContent(content)
    fun place(x: Int, y: Int, width: Int, height: Int, animate: Boolean = false) {
        settling?.cancel(); settling = null
        val fromX = params.x; val fromY = params.y
        val canAnimate = animate && attached && params.width == width && params.height == height
        params.width = width; params.height = height
        if (canAnimate && (fromX != x || fromY != y)) {
            settling = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 260; interpolator = DecelerateInterpolator()
                addUpdateListener {
                    val fraction = it.animatedValue as Float
                    params.x = (fromX + (x - fromX) * fraction).toInt()
                    params.y = (fromY + (y - fromY) * fraction).toInt()
                    if (attached) wm.updateViewLayout(view, params)
                }
                start()
            }
        } else {
            params.x = x; params.y = y
            if (attached) wm.updateViewLayout(view, params) else { wm.addView(view, params); attached = true }
        }
    }
    fun close() { settling?.cancel(); settling = null; if (attached) { runCatching { wm.removeViewImmediate(view) }; attached = false }; view.disposeComposition(); owner.destroy() }
}
