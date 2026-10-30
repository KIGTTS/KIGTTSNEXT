package com.kigtts.next.overlay

/** Input events may arrive faster than display frames. Submit only the latest window geometry. */
internal class OverlayFrameUpdates(private val schedule: (Runnable) -> Unit, private val apply: () -> Unit) {
    private var pending = false
    private var closed = false
    private val frame = Runnable {
        if (pending) flush()
    }
    fun request() {
        if (closed || pending) return
        pending = true
        schedule(frame)
    }
    /** Discrete resize must reach WindowManager before Compose renders rebased local geometry. */
    fun flush() {
        pending = false
        if (!closed) apply()
    }
    fun close() { closed = true }
}
