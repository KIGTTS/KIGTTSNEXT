package com.kigtts.next.ui.cards.camera

import android.graphics.SurfaceTexture
import android.view.Surface
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The real native API calls are exercised; Robolectric does not model a driver's lost EGL context. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35])
class CameraPreviewInputCleanupTest {
    private class Texture(private val failure: Throwable? = null) : SurfaceTexture(0) {
        var releases = 0
        override fun release() { releases++; super.release(); failure?.let { throw it } }
    }
    private class InputSurface(texture: SurfaceTexture, private val failure: Throwable? = null) : Surface(texture) {
        var releases = 0
        override fun release() { releases++; super.release(); failure?.let { throw it } }
    }

    @Test fun aLostContextStillReleasesBothRealInputObjectsAndNeverDeletesAnUnownedTextureName() {
        val texture = Texture(); val surface = InputSurface(texture)
        val contextFailure = IllegalStateException("Context lost"); var deletes = 0
        val failure = assertThrows(IllegalStateException::class.java) {
            releaseCameraPreviewInput(CameraGlInput(17, texture, surface), { throw contextFailure }) { deletes++ }
        }
        assertSame(contextFailure, failure)
        assertEquals(1, surface.releases); assertEquals(1, texture.releases)
        assertEquals(0, deletes); assertEquals(0, failure.suppressed.size)
        // SurfaceTexture.isReleased is a no-op native stub in Robolectric's empty SurfaceTexture shadow.
        // Counts include super.release(); they prove invocation, not hardware BufferQueue reclamation.
    }
    @Test fun theFirstContextErrorSurvivesBothCleanupErrorsAndBothResourcesAreAttempted() {
        val contextFailure = IllegalStateException("Context lost")
        val surfaceFailure = IllegalArgumentException("Surface release failed")
        val textureFailure = IllegalStateException("Texture release failed")
        val texture = Texture(textureFailure); val surface = InputSurface(texture, surfaceFailure)
        val failure = assertThrows(IllegalStateException::class.java) {
            releaseCameraPreviewInput(CameraGlInput(17, texture, surface), { throw contextFailure }) { fail("Wrong context") }
        }
        assertSame(contextFailure, failure)
        assertArrayEquals(arrayOf(surfaceFailure, textureFailure), failure.suppressed)
        assertEquals(1, surface.releases); assertEquals(1, texture.releases)
    }
    @Test fun aValidContextDeletesItsTextureOnlyAfterNativeResourcesAreReleasedAndKeepsDeleteFailures() {
        val texture = Texture(); val surface = InputSurface(texture); var deleted = -1
        val deletionFailure = IllegalStateException("Texture name deletion failed")
        val failure = assertThrows(IllegalStateException::class.java) {
            releaseCameraPreviewInput(CameraGlInput(17, texture, surface), {}) { name ->
                assertEquals(1, surface.releases); assertEquals(1, texture.releases)
                deleted = name; throw deletionFailure
            }
        }
        assertSame(deletionFailure, failure); assertEquals(17, deleted)
    }
}
