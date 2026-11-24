package com.kigtts.next.state

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Null means that the first deliberate hold still needs an explanation. */
class QuickSortPreference(context: Context) {
    private val preferences = context.getSharedPreferences("interaction", Context.MODE_PRIVATE)
    private val choice = MutableStateFlow(if (preferences.contains("quick-sort")) preferences.getBoolean("quick-sort", false) else null)
    private val asking = MutableStateFlow(false)
    val enabled = choice.asStateFlow()
    val prompt = asking.asStateFlow()
    internal fun offer() {
        if (choice.value == null) asking.value = true
    }
    fun begin(): Boolean {
        offer()
        return choice.value == true
    }
    fun choose(enabled: Boolean) {
        preferences.edit().putBoolean("quick-sort", enabled).apply()
        choice.value = enabled
        asking.value = false
    }
    fun dismiss() = choose(false)
}
