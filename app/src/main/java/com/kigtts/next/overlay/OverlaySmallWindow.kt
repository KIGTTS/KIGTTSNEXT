package com.kigtts.next.overlay

import android.app.ActivityOptions
import android.content.Context
import android.os.Build
import android.os.Bundle

/** Bounds alone do not request freeform. A ROM may still reject the requested mode. */
internal object OverlaySmallWindow {
    fun options(context: Context, targetPackage: String = context.packageName): Bundle {
        if (Build.MANUFACTURER.equals("Xiaomi", true)) {
            OverlayMiuiWindow.options(context, targetPackage)?.let { return it.toBundle() }
        }
        // Let the system choose its native freeform aspect, scale and density.
        val options = ActivityOptions.makeBasic()
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
