package com.kigtts.next.overlay

import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass

/** Only window-options classes, never a global exemption or Android permission grant. */
internal object OverlayPlatformAccess {
    private val requested by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) false else runCatching {
            HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/util/MiuiMultiWindowUtils;", "Landroid/app/ActivityOptions;",
                "Landroid/app/ActivityOptions\$ActivityOptionsInjector;")
        }.getOrDefault(false)
    }
    fun requestWindowOptions() { requested }
}
