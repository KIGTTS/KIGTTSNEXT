package com.kigtts.next.ui.cards.camera

import androidx.camera.core.CameraEffect
import androidx.core.util.Consumer
import java.io.Closeable

/** Ordinary recording shares the preview grade. JPEG and Motion Photo keep the original capture. */
internal class CameraPreviewEffect internal constructor(private val processor: CameraPreviewSurfaceProcessor, video: Boolean = false) :
    CameraEffect(if (video) PREVIEW or VIDEO_CAPTURE else PREVIEW, processor.executor, processor, Consumer(processor::reportFailure)), Closeable {
    constructor(colors: CameraPreviewColors, video: Boolean, failure: (Throwable) -> Unit) :
        this(CameraPreviewSurfaceProcessor(colors, failure, createRenderer = { CameraPreviewGlRenderer(video) }), video)
    val failure: Throwable? get() = processor.failure
    override fun close() = processor.close()
}
