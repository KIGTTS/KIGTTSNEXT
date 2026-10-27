package com.kigtts.next.state

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Unset until the user chooses a return habit in the first root-back dialog or Settings. */
class HomeExitPreference(context: Context) {
    private val preferences = context.getSharedPreferences("interaction", Context.MODE_PRIVATE)
    private val choice = MutableStateFlow(if (preferences.contains("double-back-exit")) preferences.getBoolean("double-back-exit", true) else null)
    val doubleBack = choice.asStateFlow()
    fun choose(enabled: Boolean) {
        preferences.edit().putBoolean("double-back-exit", enabled).apply()
        choice.value = enabled
    }
}

internal enum class HomeExitAction { ASK, HINT, LEAVE }

internal class HomeExitGate {
    private var armedAt: Long? = null
    fun reset() { armedAt = null }
    fun back(doubleBack: Boolean?, now: Long): HomeExitAction {
        if (doubleBack == null) { reset(); return HomeExitAction.ASK }
        if (!doubleBack) { reset(); return HomeExitAction.LEAVE }
        if (armedAt?.let { now - it in 0L..2000L } == true) { reset(); return HomeExitAction.LEAVE }
        armedAt = now
        return HomeExitAction.HINT
    }
}
