package com.kigtts.next.overlay

import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator

/** Keep native input bounds and visual progress on one platform animation clock. */
internal class OverlayEdgeMotion(private val frame: (Float) -> Unit) {
    private var animation: ValueAnimator? = null
    private var value = 0f
    private var target = 0f

    fun animateTo(next: Float) {
        if (target == next) return
        animation?.cancel()
        target = next
        animation = ValueAnimator.ofFloat(value, next).apply {
            duration = 220
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { value = it.animatedValue as Float; frame(value) }
            start()
        }
    }

    fun snapTo(next: Float) {
        animation?.cancel(); animation = null
        target = next; value = next; frame(next)
    }

    fun close() { animation?.cancel(); animation = null }
}
