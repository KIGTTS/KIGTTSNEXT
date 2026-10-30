package com.kigtts.next.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.compose.ui.geometry.Offset
import kotlin.math.roundToInt

/** Android 10/11 can latch a resized buffer and its new window origin in separate frames. */
internal class OverlayResizeCover(private val context: Context, private val wm: WindowManager) {
    private val main = Handler(Looper.getMainLooper())
    private var cover: ImageView? = null
    private var committing = false
    private var revealing = false
    var preparing = false; private set
    val active get() = cover != null
    private var generation = 0

    fun begin(view: View, params: WindowManager.LayoutParams, anchor: Offset, ready: () -> Unit): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        if (active && !revealing) return true
        if (active) close()
        if (!view.isAttachedToWindow || !view.isHardwareAccelerated || view.width == 0 || view.height == 0) return false
        val radius = 36 * context.resources.displayMetrics.density
        val crop = Rect((anchor.x - radius).roundToInt().coerceAtLeast(0), (anchor.y - radius).roundToInt().coerceAtLeast(0),
            (anchor.x + radius).roundToInt().coerceAtMost(view.width), (anchor.y + radius).roundToInt().coerceAtMost(view.height))
        if (crop.isEmpty) return false
        val image = runCatching {
            val bitmap = Bitmap.createBitmap(crop.width(), crop.height(), Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap).apply { translate(-crop.left.toFloat(), -crop.top.toFloat()) })
            ImageView(context).apply { setImageBitmap(bitmap) }
        }.getOrNull() ?: return false
        val position = IntArray(2); view.getLocationOnScreen(position)
        val snapshot = WindowManager.LayoutParams().apply {
            copyFrom(params); x = position[0] + crop.left; y = position[1] + crop.top
            width = crop.width(); height = crop.height()
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            title = "KIGTTS 悬浮窗过渡"
        }
        if (runCatching { wm.addView(image, snapshot) }.isFailure) return false
        cover = image; preparing = true; committing = false; revealing = false
        val current = ++generation
        fun prepared() {
            if (generation != current || !preparing) return
            preparing = false; ready()
        }
        image.viewTreeObserver.registerFrameCommitCallback { main.post { prepared() } }
        // Screen-off, a detached surface or an OEM that omits commit callbacks cannot block the dial.
        main.postDelayed({ prepared() }, 100)
        return true
    }

    fun drawn(view: View, matches: () -> Boolean, reveal: () -> Unit) {
        if (Build.VERSION.SDK_INT < 29) return
        if (!active || preparing || committing || revealing) return
        committing = true
        val current = generation
        view.viewTreeObserver.registerFrameCommitCallback {
            main.post {
                if (generation != current || !active) return@post
                committing = false
                if (!matches()) { view.postInvalidateOnAnimation(); return@post }
                revealing = true; reveal()
                view.viewTreeObserver.registerFrameCommitCallback { main.post { if (generation == current) close() } }
                view.postInvalidateOnAnimation()
                main.postDelayed({ if (generation == current) close() }, 100)
            }
        }
        // Retain the old ball until the first correctly sized frame has entered the swap chain.
        main.postDelayed({
            if (generation == current && active && !revealing) { revealing = true; reveal(); close() }
        }, 250)
    }

    fun close() {
        generation++; preparing = false; committing = false; revealing = false
        cover?.let { image -> runCatching { wm.removeViewImmediate(image) }; image.setImageDrawable(null) }
        cover = null
    }
}
