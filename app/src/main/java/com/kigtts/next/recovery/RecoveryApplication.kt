package com.kigtts.next.recovery

import android.app.Application
import android.app.WallpaperManager
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.kigtts.next.desktop.DesktopUpdates

class RecoveryApplication : Application() {
    private var handlesDesktop = false

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (handlesDesktop) DesktopUpdates.refresh(this)
    }

    override fun onCreate() {
        super.onCreate()
        val recovery = CrashRecovery(this)
        val process = if (android.os.Build.VERSION.SDK_INT >= 28) getProcessName() else {
            val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
            manager.runningAppProcesses?.firstOrNull { it.pid == android.os.Process.myPid() }?.processName ?: packageName
        }
        handlesDesktop = process == packageName
        if (handlesDesktop && Build.VERSION.SDK_INT >= 31) runCatching {
            WallpaperManager.getInstance(this).addOnColorsChangedListener({ _, which ->
                if (which and WallpaperManager.FLAG_SYSTEM != 0) DesktopUpdates.refresh(this)
            }, Handler(Looper.getMainLooper()))
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, failure ->
            runCatching { recovery.record(failure, process == packageName) }
            if (previous != null) previous.uncaughtException(thread, failure)
            else { android.os.Process.killProcess(android.os.Process.myPid()); kotlin.system.exitProcess(10) }
        }
    }
}
