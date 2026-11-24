package com.kigtts.next.ui.onboarding

import android.animation.ValueAnimator
import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.widget.FrameLayout
import android.widget.ImageView
import android.view.Gravity
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import com.kigtts.next.R

/** The platform owns the starting window. Adopt the loaded palette at handoff. */
internal class NativeStartup(private val activity: Activity) {
    private var background: Int? = null
    private var foreground: Int? = null
    private var finished = false

    fun ready(colors: ColorScheme) {
        if (finished) return
        val background = colors.background.toArgb()
        if (this.background != background) activity.window.setBackgroundDrawable(ColorDrawable(background))
        this.background = background
        foreground = colors.primary.toArgb()
    }

    fun install() {
        if (Build.VERSION.SDK_INT < 31) return
        activity.splashScreen.setOnExitAnimationListener { splash ->
            finished = true
            val tint = foreground
            if (background == null || tint == null || !ValueAnimator.areAnimatorsEnabled()) {
                splash.remove()
                return@setOnExitAnimationListener
            }
            splash.setBackgroundColor(checkNotNull(background))
            val icon = splash.iconView
            if (icon is ImageView) icon.imageTintList = ColorStateList.valueOf(tint)
            else {
                icon?.alpha = 0f
                val size = (96 * activity.resources.displayMetrics.density).toInt()
                splash.addView(ImageView(activity).apply {
                    setImageResource(R.drawable.ic_launcher_monochrome)
                    imageTintList = ColorStateList.valueOf(tint)
                }, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
            }
            splash.animate().alpha(0f).setDuration(160).withEndAction(splash::remove).start()
        }
    }
}
