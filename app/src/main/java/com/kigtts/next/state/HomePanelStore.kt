package com.kigtts.next.state

import android.content.Context

class HomePanelStore(context: Context) {
    private val preferences = context.getSharedPreferences("home-panel", Context.MODE_PRIVATE)
    fun loadHeight(): Float = preferences.getFloat("expanded-height", HomePanelSizes.PEEK)
        .takeIf { it.isFinite() && it > HomePanelSizes.COLLAPSED } ?: HomePanelSizes.PEEK
    fun saveHeight(height: Float) { preferences.edit().putFloat("expanded-height", height).apply() }
}
