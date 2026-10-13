package com.kigtts.next.ui.cards.camera

import androidx.camera.core.CameraEffect
import androidx.core.util.Consumer
import java.io.Closeable

/** PREVIEW only: raw JPEG and Motion Photo video keep their independent single-filter paths. */
internal class CameraPreviewEffect internal constructor(private val processor: CameraPreviewSurfaceProcessor) :
    CameraEffect(PREVIEW, processor.executor, processor, Consumer(processor::reportFailure)), Closeable {
    constructor(colors: CameraPreviewColors, failure: (Throwable) -> Unit) : this(CameraPreviewSurfaceProcessor(colors, failure))
    val failure: Throwable? get() = processor.failure
    override fun close() = processor.close()
}
