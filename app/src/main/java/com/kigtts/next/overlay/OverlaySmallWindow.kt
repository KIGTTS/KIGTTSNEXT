package com.kigtts.next.overlay

import android.app.ActivityOptions
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Bundle

/** Bounds alone do not request freeform. A ROM may still reject the requested mode. */
internal object OverlaySmallWindow {
    fun options(context: Context): Bundle {
        val metrics = context.resources.displayMetrics
        val options = ActivityOptions.makeBasic().setLaunchBounds(Rect(
            (metrics.widthPixels * .15f).toInt(), (metrics.heightPixels * .15f).toInt(),
            (metrics.widthPixels * .85f).toInt(), (metrics.heightPixels * .85f).toInt()))
        // Some ROMs expose the setter; others restrict reflection. The serialized AOSP
        // option still requests the same mode without assuming the advertised feature flag.
        runCatching {
            val method = if (Build.VERSION.SDK_INT >= 28) "setLaunchWindowingMode" else "setLaunchStackId"
            ActivityOptions::class.java.getMethod(method, Int::class.javaPrimitiveType)
                .invoke(options, if (Build.VERSION.SDK_INT >= 28) 5 else 2)
        }
        return options.toBundle().apply {
            putInt(if (Build.VERSION.SDK_INT >= 28) "android.activity.windowingMode" else "android.activity.launchStackId",
                if (Build.VERSION.SDK_INT >= 28) 5 else 2)
        }
    }
}
