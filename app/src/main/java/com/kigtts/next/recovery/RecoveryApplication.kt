package com.kigtts.next.recovery

import android.app.Application

class RecoveryApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val recovery = CrashRecovery(this)
        val process = if (android.os.Build.VERSION.SDK_INT >= 28) getProcessName() else {
            val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
            manager.runningAppProcesses?.firstOrNull { it.pid == android.os.Process.myPid() }?.processName ?: packageName
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, failure ->
            runCatching { recovery.record(failure, process == packageName) }
            if (previous != null) previous.uncaughtException(thread, failure)
            else { android.os.Process.killProcess(android.os.Process.myPid()); kotlin.system.exitProcess(10) }
        }
    }
}
