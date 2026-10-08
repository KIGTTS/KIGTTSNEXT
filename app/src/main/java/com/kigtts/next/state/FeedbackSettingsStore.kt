package com.kigtts.next.state

import android.content.Context

class FeedbackSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("interaction", Context.MODE_PRIVATE)
    fun load(): Boolean = preferences.getBoolean("haptics", true)
    fun save(enabled: Boolean) { preferences.edit().putBoolean("haptics", enabled).apply() }
}
