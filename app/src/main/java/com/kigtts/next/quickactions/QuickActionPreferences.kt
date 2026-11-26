package com.kigtts.next.quickactions

import android.content.Context
import androidx.core.content.edit

internal class QuickActionPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("quick-actions", Context.MODE_PRIVATE)

    var lockScreenShortcut: Boolean
        get() = preferences.getBoolean("lock-screen-shortcut", false)
        set(value) { preferences.edit { putBoolean("lock-screen-shortcut", value) } }

    var bluetoothSubtitles: Boolean
        get() = preferences.getBoolean("bluetooth-subtitles", false)
        set(value) { preferences.edit { putBoolean("bluetooth-subtitles", value) } }
}
