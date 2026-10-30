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
import androidx.compose.ui.geometry.Offset

/** Geometry already applies absolute system-bar/cutout insets; WindowManager must not apply them again. */
internal fun overlayWindowParams() = WindowManager.LayoutParams(80, 80, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, PixelFormat.TRANSLUCENT).apply {
    windowAnimations = 0
    softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
    // WindowManager can otherwise animate a resize relocation independently of our morph.
    runCatching {
        val flags = WindowManager.LayoutParams::class.java.getField("privateFlags")
        val noMove = WindowManager.LayoutParams::class.java.getField("PRIVATE_FLAG_NO_MOVE_ANIMATION").getInt(null)
        flags.setInt(this, flags.getInt(this) or noMove)
    }
    gravity = Gravity.TOP or Gravity.LEFT; title = "KIGTTS 悬浮快捷入口"
    if (Build.VERSION.SDK_INT >= 30) {
        setFitInsetsTypes(0)
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }
}

internal class OverlayWindow(context: Context, private val wm: WindowManager, outside: () -> Unit, private val failed: () -> Unit = {},
    private val transitionAnchor: (() -> Offset)? = null) {
    private val owner = OverlayViewOwner()
    private val view = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setOnTouchListener { _, event -> if (event.action == MotionEvent.ACTION_OUTSIDE) { outside(); true } else false }
    }
    private val params = overlayWindowParams()
    private val resizeCover = if (Build.VERSION.SDK_INT in 29..30 && transitionAnchor != null) OverlayResizeCover(context, wm) else null
    private var attached = false
    private var settling: ValueAnimator? = null
    private val resizeGate = OverlayResizeGate()
    private val updates = OverlayFrameUpdates({ view.postOnAnimation(it) }) {
        if (attached && resizeCover?.preparing != true) try { wm.updateViewLayout(view, params) }
        catch (_: RuntimeException) { close(); failed() }
    }
    private var target: android.graphics.Rect? = null
    private val screenLocation = IntArray(2)
    init {
        owner.attach(view)
        // Geometry and WindowManager resize arrive through separate frame phases.
        // Do not paint new local FAB coordinates into the old-sized surface.
        view.viewTreeObserver.addOnPreDrawListener {
            view.getLocationOnScreen(screenLocation)
            val draw = resizeCover?.preparing != true && (!attached || resizeGate.canDraw(
                sizeMatches = view.width == params.width && view.height == params.height,
                positionMatches = screenLocation[0] == params.x && screenLocation[1] == params.y))
            if (draw) resizeCover?.drawn(view, matches = {
                view.getLocationOnScreen(screenLocation)
                view.width == params.width && view.height == params.height && screenLocation[0] == params.x && screenLocation[1] == params.y
            }, reveal = { params.alpha = 1f; updates.flush() })
            if (!draw) view.postInvalidateOnAnimation()
            draw
        }
    }
    fun content(content: @Composable () -> Unit) = view.setContent(content)
    fun stopAtCurrentPosition(placement: OverlayPlacement): OverlayPlacement {
        settling?.cancel(); settling = null
        target = null
        return placement.copy(x = params.x, y = params.y, width = params.width, height = params.height)
    }
    fun place(x: Int, y: Int, width: Int, height: Int, animate: Boolean = false, continuousResize: Boolean = false) {
        val next = android.graphics.Rect(x, y, x + width, y + height)
        if (attached && target == next) return
        target = next
        settling?.cancel(); settling = null
        val fromX = params.x; val fromY = params.y
        val canAnimate = animate && attached && params.width == width && params.height == height
        val resized = params.width != width || params.height != height
        if (resized && attached && !continuousResize && resizeCover?.begin(view, params, transitionAnchor!!.invoke()) { updates.flush() } == true) {
            params.alpha = 0f
        }
        if (resized) resizeGate.resizing(continuousResize)
        params.width = width; params.height = height
        if (canAnimate && (fromX != x || fromY != y)) {
            settling = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 260; interpolator = DecelerateInterpolator()
                addUpdateListener {
                    val fraction = it.animatedValue as Float
                    params.x = (fromX + (x - fromX) * fraction).toInt()
                    params.y = (fromY + (y - fromY) * fraction).toInt()
                    updates.request()
                }
                start()
            }
        } else {
            params.x = x; params.y = y
            if (attached) {
                if (resized && !continuousResize) updates.flush() else updates.request()
            } else { wm.addView(view, params); attached = true }
        }
    }
    fun close() { updates.close(); resizeCover?.close(); settling?.cancel(); settling = null; if (attached) { runCatching { wm.removeViewImmediate(view) }; attached = false }; view.disposeComposition(); owner.destroy() }
}

/** A ROM can constrain overlay size permanently (IME, rotation, split screen). Never stop drawing forever. */
internal class OverlayResizeGate {
    private var frames = 0
    fun resizing(continuous: Boolean = false) { frames = if (continuous) 0 else 3 }
    fun canDraw(sizeMatches: Boolean, positionMatches: Boolean = true): Boolean {
        if (sizeMatches && positionMatches) frames = 0
        if (frames == 0) return true
        frames--
        return false
    }
}
