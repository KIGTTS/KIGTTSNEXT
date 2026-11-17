package com.kigtts.engines.recognitionservice

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

class SystemRecognitionPermissionIssue internal constructor(val appPackage: String?, val appName: String?) :
    IllegalStateException("系统识别引擎没有麦克风权限")

@Suppress("DEPRECATION")
internal fun systemRecognitionPermissionIssue(context: Context): SystemRecognitionPermissionIssue {
    val target = if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
        context.packageName
    else runCatching { Settings.Secure.getString(context.contentResolver, "voice_recognition_service")
        ?.let(ComponentName::unflattenFromString)?.packageName }.getOrNull()
    val info = target?.let { runCatching { context.packageManager.getApplicationInfo(it, 0) }.getOrNull() }
    val label = info?.loadLabel(context.packageManager)?.toString()?.trim()?.takeIf(String::isNotEmpty)
    return SystemRecognitionPermissionIssue(info?.packageName, label)
}
