package com.kigtts.next.ui.cards.camera

import android.os.Handler
import android.os.HandlerThread
import java.io.Closeable
import java.util.concurrent.Executor

/** Narrow thread ownership lets protocol tests control dispatch without faking CameraX surface futures. */
internal interface CameraPreviewWorker : Closeable {
    val handler: Handler
    val executor: Executor
}

internal class CameraPreviewGlWorker : CameraPreviewWorker {
    private val thread = HandlerThread("KigCameraPreviewGL").apply { start() }
    override val handler = Handler(thread.looper)
    // After final release, late callbacks only enter the processor's guarded, no-GL branches.
    override val executor = Executor { task -> if (!handler.post(task)) task.run() }
    override fun close() { thread.quitSafely() }
}
