package com.kigtts.next.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp

/** Animate absolute coordinates while keeping the input window tight around the ball. */
internal class OverlayDockMotion(private val frame: (Offset) -> Unit, private val finished: () -> Unit) {
    private var animation: ValueAnimator? = null
    fun cancel() { animation?.cancel(); animation = null }
    fun start(from: Offset, to: Offset) {
        cancel()
        animation = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener { frame(lerp(from, to, it.animatedValue as Float)) }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) { this@OverlayDockMotion.animation = null; finished() }
                }
            })
            start()
        }
    }
}
