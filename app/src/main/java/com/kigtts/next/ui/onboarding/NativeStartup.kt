package com.kigtts.next.ui.onboarding

import android.animation.ValueAnimator
import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.doOnPreDraw
import com.kigtts.next.R
import kotlin.math.roundToInt

private data class StartupColors(val background: Int, val wordmark: Int)

/** The platform owns the starting window. Adopt the loaded palette at handoff. */
internal class NativeStartup(private val activity: Activity) {
    private var palette: StartupColors? = null
    private var legacy: FrameLayout? = null
    private var finished = false

    fun ready(colors: ColorScheme) {
        if (finished) return
        val current = StartupColors(
            background = colors.background.toArgb(),
            wordmark = lerp(colors.onSurfaceVariant, colors.primary, 0.16f).toArgb(),
        )
        if (palette?.background != current.background) {
            activity.window.setBackgroundDrawable(ColorDrawable(current.background))
        }
        palette = current
        finishLegacy()
    }

    fun install() {
        if (Build.VERSION.SDK_INT >= 31) {
            activity.splashScreen.setOnExitAnimationListener { splash ->
                finished = true
                palette?.let { current ->
                    splash.iconView?.visibility = View.INVISIBLE
                    paint(splash, current)
                }
                dismiss(splash, splash::remove)
            }
        } else if (ValueAnimator.areAnimatorsEnabled()) {
            val root = activity.findViewById<FrameLayout>(android.R.id.content)
            legacy = FrameLayout(activity).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                setBackgroundResource(R.drawable.startup_background)
                root.addView(this, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ))
            }
            finishLegacy()
        }
    }

    private fun finishLegacy() {
        val cover = legacy ?: return
        val current = palette ?: return
        legacy = null
        finished = true
        paint(cover, current)
        cover.doOnPreDraw { dismiss(cover) { (cover.parent as? ViewGroup)?.removeView(cover) } }
    }

    private fun paint(frame: FrameLayout, colors: StartupColors) {
        frame.setBackgroundColor(colors.background)
        val metrics = activity.resources.displayMetrics
        val preferredWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 180f, metrics)
        val width = minOf(preferredWidth, metrics.widthPixels * 0.75f).roundToInt().coerceAtLeast(1)
        frame.addView(ImageView(activity).apply {
            setImageResource(R.drawable.logo_horizontal)
            imageTintList = ColorStateList.valueOf(colors.wordmark)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, FrameLayout.LayoutParams(width, (width / 4f).roundToInt().coerceAtLeast(1), Gravity.CENTER))
    }

    private fun dismiss(view: View, remove: () -> Unit) {
        if (!ValueAnimator.areAnimatorsEnabled()) remove()
        else view.animate().alpha(0f).setDuration(160).withEndAction(remove).start()
    }
}
