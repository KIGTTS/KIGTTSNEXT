package com.kigtts.next.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Growing transcript height does not cancel follow; moving back through existing content does. */
internal class ListeningFollowState(initial: Boolean = true) {
    var following by mutableStateOf(initial)
        private set
    private var previousValue: Int? = null
    private var previousMaximum: Int? = null
    fun userMovedTowardHistory() { following = false }
    fun observe(value: Int, maximum: Int) {
        if (maximum == Int.MAX_VALUE) return // ScrollState is not measured yet.
        if (previousMaximum == maximum && previousValue?.let { value < it } == true) following = false
        if (value >= maximum - 2) following = true
        previousValue = value; previousMaximum = maximum
    }
}
