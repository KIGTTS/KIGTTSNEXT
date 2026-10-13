package com.kigtts.next.ui.cards.camera

/** Native surfaces must be released even when this EGL context has already been lost. */
internal fun releaseCameraPreviewInput(input: CameraGlInput, makeCurrent: () -> Unit, deleteTexture: (Int) -> Unit) {
    var first: Throwable? = null
    var ownsContext = false
    fun attempt(cleanup: () -> Unit) {
        try { cleanup() }
        catch (failure: Throwable) {
            val initial = first
            if (initial == null) first = failure else if (initial !== failure) initial.addSuppressed(failure)
        }
    }
    attempt { makeCurrent(); ownsContext = true }
    attempt { input.texture.setOnFrameAvailableListener(null) }
    attempt { input.surface.release() }
    attempt { input.texture.release() }
    // Never delete a name in an unrelated/current-but-invalid GL context.
    if (ownsContext) attempt { deleteTexture(input.textureId) }
    first?.let { throw it }
}
