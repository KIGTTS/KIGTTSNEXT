package com.kigtts.next.overlay

import android.app.ActivityOptions
import android.content.Context
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.WindowManager

/** MIUI bounds are logical pixels; the matching surface scale must travel with them. */
internal object OverlayMiuiWindow {
    fun options(context: Context, targetPackage: String): ActivityOptions? = runCatching {
        OverlayPlatformAccess.requestWindowOptions()
        val display = context.getSystemService(DisplayManager::class.java)
            .getDisplay(Display.DEFAULT_DISPLAY) ?: return null
        val displayContext = context.createDisplayContext(display)
        // Do not measure against the current task: it may already be a small window.
        val screen = if (Build.VERSION.SDK_INT >= 30) displayContext.createWindowContext(
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null) else displayContext
        val platform = Class.forName("android.util.MiuiMultiWindowUtils")
        val boolean = Boolean::class.javaPrimitiveType!!
        val integer = Int::class.javaPrimitiveType!!
        val taskInfo = Class.forName("android.app.TaskInfo")
        val normal = platform.getMethod("isNormalFreeForm", Context::class.java, String::class.java, taskInfo)
            .invoke(null, screen, targetPackage, null) as Boolean
        val orientation = screen.packageManager.getLaunchIntentForPackage(targetPackage)?.let {
            screen.packageManager.resolveActivity(it, 0)?.activityInfo?.screenOrientation
        }
        val landscape = orientation in setOf(0, 6, 8, 11) // landscape / sensor / reverse / user
        val bounds = platform.getMethod("getCustomFreeformRect", Context::class.java, integer, integer,
            boolean, String::class.java, boolean)
            .invoke(null, screen, -1, -1, landscape, targetPackage, normal) as Rect
        val scale = platform.getMethod("getOriFreeformScale", Context::class.java, boolean, boolean,
            boolean, String::class.java)
            .invoke(null, screen, landscape, normal, false, targetPackage) as Float
        require(!bounds.isEmpty && scale.isFinite() && scale > 0f && scale <= 1f)
        val options = ActivityOptions.makeBasic()
        val injector = ActivityOptions::class.java.getMethod("getActivityOptionsInjector").invoke(options)
        injector.javaClass.getMethod("setFreeformScale", Float::class.javaPrimitiveType).invoke(injector, scale)
        injector.javaClass.getMethod("setNormalFreeForm", boolean).invoke(injector, normal)
        ActivityOptions::class.java.getMethod("setLaunchWindowingMode", integer).invoke(options, 5)
        ActivityOptions::class.java.getMethod("setMiuiConfigFlag", integer).invoke(options, 2)
        options.setLaunchBounds(bounds)
        Log.d("KigttsOverlayWindow", "MIUI bounds=$bounds scale=$scale density=${screen.resources.displayMetrics.densityDpi}")
        options
    }.onFailure {
        Log.w("KigttsOverlayWindow", "System small-window geometry unavailable: ${it.javaClass.simpleName}")
    }.getOrNull()
}
