package com.kigtts.next.ui.cards.camera

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*

/** A cancellable delay only. Camera exposure/saving ownership begins in the single ready callback. */
internal class CameraCountdownState(private val scope: CoroutineScope) {
    var active by mutableStateOf(false); private set
    var remainingSeconds by mutableIntStateOf(0); private set
    private var job: Job? = null
    private var version = 0L
    private var cancelled: (() -> Unit)? = null

    fun start(seconds: Int, shoot: () -> Unit, onCancelled: () -> Unit): Boolean {
        if (active || !scope.isActive || seconds !in listOf(0, 3, 10)) return false
        if (seconds == 0) { shoot(); return true }
        val current = ++version
        active = true; remainingSeconds = seconds; cancelled = onCancelled
        val task = scope.launch(start = CoroutineStart.LAZY) {
            var handedOff = false
            try {
                for (remaining in seconds downTo 1) {
                    remainingSeconds = remaining
                    delay(1_000)
                }
                currentCoroutineContext().ensureActive()
                if (current == version) {
                    remainingSeconds = 0
                    shoot(); handedOff = true; cancelled = null
                }
            } finally {
                if (current == version) {
                    active = false; remainingSeconds = 0; job = null
                    val cleanup = cancelled; cancelled = null
                    if (!handedOff) cleanup?.invoke()
                }
            }
        }
        job = task
        if (!task.start()) { cancel(); return false }
        return true
    }

    /** Explicit cancellation restores the selected camera controls synchronously; the old job cannot fire later. */
    fun cancel(): Boolean {
        if (!active) return false
        version++; active = false; remainingSeconds = 0
        val task = job; job = null
        val cleanup = cancelled; cancelled = null
        task?.cancel(); cleanup?.invoke()
        return true
    }
}
