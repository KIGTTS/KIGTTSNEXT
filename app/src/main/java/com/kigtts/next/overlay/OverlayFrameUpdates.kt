package com.kigtts.next.overlay

/** Input events may arrive faster than display frames. Submit only the latest window geometry. */
internal class OverlayFrameUpdates(private val schedule: (Runnable) -> Unit, private val apply: () -> Unit) {
    private var pending = false
    private var closed = false
    private val frame = Runnable {
        pending = false
        if (!closed) apply()
    }
    fun request() {
        if (closed || pending) return
        pending = true
        schedule(frame)
    }
    fun close() { closed = true }
}
