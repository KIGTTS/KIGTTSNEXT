package com.kigtts.next.state

import android.content.Context

class FeedbackSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("interaction", Context.MODE_PRIVATE)
    fun load(): Boolean = preferences.getBoolean("haptics", true)
    fun save(enabled: Boolean) { preferences.edit().putBoolean("haptics", enabled).apply() }
    fun loadMediaCollapse(): MediaQuickTextCollapse =
        MediaQuickTextCollapse.entries.find { it.name == preferences.getString("media-quick-text-collapse", null) }
            ?: MediaQuickTextCollapse.KEEP
    fun saveMediaCollapse(value: MediaQuickTextCollapse) {
        preferences.edit().putString("media-quick-text-collapse", value.name).apply()
    }
}
