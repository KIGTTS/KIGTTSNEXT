package com.kigtts.next.ui.cards.camera

import android.app.Application
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.kigtts.plugin.api.PolaroidFormat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.ByteArrayOutputStream
import java.io.File

/** Encoded JPEG/MP4 bytes are real; only Android's hardware metadata reader is replaced. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [26, 35], shadows = [CameraGalleryCaptureTest.Metadata::class])
class CameraGalleryCaptureTest {
    @get:Rule val temporary = TemporaryFolder()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    @Before fun resetMetadata() { Metadata.width = "96"; Metadata.released = 0 }
    private fun jpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(48, 62, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        return try { ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 96, it) }.toByteArray() }
        finally { bitmap.recycle() }
    }
    private fun clip() = requireNotNull(javaClass.classLoader!!.getResourceAsStream("motion/blue.mp4")).use { it.readBytes() }
    private fun source(bytes: ByteArray) = temporary.newFile().apply { writeBytes(bytes) }
    private fun embedded(still: ByteArray, video: ByteArray): ByteArray {
        val xmp = """<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"><rdf:Description xmlns:GCamera="http://ns.google.com/photos/1.0/camera/" GCamera:MotionPhoto="1" GCamera:MicroVideoOffset="${video.size}"><GCamera:MotionPhotoPresentationTimestampUs>600000</GCamera:MotionPhotoPresentationTimestampUs></rdf:Description></rdf:RDF></x:xmpmeta>"""
        return still + xmp.toByteArray(Charsets.UTF_8)
    }

    @Test fun normalPhotoPreservesItsEncodedBytesAndSelectionOwnershipWithoutCreatingMotion() = runBlocking {
        val bytes = jpeg(); val selected = source(bytes)
        val result = cameraGalleryCapture(app, Uri.fromFile(selected), PolaroidFormat.WIDE)
        try {
            assertArrayEquals(bytes, result.photo.original.readBytes())
            assertSame(result.photo.original, result.photo.displayFile)
            assertEquals(PolaroidFormat.WIDE, result.photo.format)
            assertNull(result.photo.motion); assertNull(result.warning)
            assertTrue(selected.isFile)
        } finally { result.photo.discard() }
        assertFalse(result.photo.original.exists()); assertTrue(selected.isFile)
    }

    @Test fun androidMotionImportsTheOriginalEncodedStillAndExactVideoWithItsPresentationTimestamp() = runBlocking {
        val video = clip(); val still = embedded(jpeg(), video); val selected = source(still + video)
        val result = cameraGalleryCapture(app, Uri.fromFile(selected), PolaroidFormat.SQUARE)
        val motion = requireNotNull(result.photo.motion)
        try {
            assertArrayEquals(still, result.photo.original.readBytes())
            assertArrayEquals(video, motion.video.readBytes())
            assertEquals(1000L, motion.durationMs); assertEquals(600L, motion.shutterPositionMs)
            assertEquals(90, motion.rotationDegrees)
            assertArrayEquals(PhotoFilters.coefficients(PhotoFilter.ORIGINAL), motion.colorMatrix, 0f)
            assertEquals(1, Metadata.released)
            assertNull(result.warning)
            assertTrue(selected.isFile)
        } finally { result.photo.close() }
        assertFalse(motion.video.exists()); assertFalse(result.photo.original.exists())
        assertTrue(selected.isFile)
    }

    @Test fun invalidEmbeddedVideoFallsBackToThePreservedPhotoAndUnreadableSelectionsLeaveNoCache() = runBlocking {
        val video = clip(); val still = embedded(jpeg(), video); Metadata.width = "0"
        val result = cameraGalleryCapture(app, Uri.fromFile(source(still + video)), PolaroidFormat.MINI)
        try {
            assertNull(result.photo.motion); assertTrue(result.warning.orEmpty().contains("保留照片"))
            assertArrayEquals(still, result.photo.original.readBytes()); assertEquals(1, Metadata.released)
        } finally { result.photo.discard() }
        val directory = File(app.cacheDir, "polaroid-camera/gallery")
        val before = directory.listFiles().orEmpty().map { it.name }.toSet()
        val unreadable = source(byteArrayOf(1, 2, 3))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { cameraGalleryCapture(app, Uri.fromFile(unreadable), PolaroidFormat.SQUARE) }
        }
        assertEquals(before, directory.listFiles().orEmpty().map { it.name }.toSet())
        assertTrue(unreadable.isFile)
    }

    @Test fun timestampSupportsBothXmpEncodingsAndAbsentOrNegativeValuesUseTheClipMidpoint() {
        assertEquals(750L, cameraGalleryShutterPosition("GCamera:MotionPhotoPresentationTimestampUs=\"750000\"", 1000))
        assertEquals(600L, cameraGalleryShutterPosition("<GCamera:MicroVideoPresentationTimestampUs>600000</GCamera:MicroVideoPresentationTimestampUs>", 1000))
        assertEquals(500L, cameraGalleryShutterPosition("", 1000))
        assertEquals(500L, cameraGalleryShutterPosition("GCamera:MotionPhotoPresentationTimestampUs=\"-1\"", 1000))
        assertEquals(1000L, cameraGalleryShutterPosition("GCamera:MotionPhotoPresentationTimestampUs=\"999999999\"", 1000))
    }

    @Implements(MediaMetadataRetriever::class)
    class Metadata {
        @Implementation fun __constructor__() {}
        @Implementation fun setDataSource(path: String) { require(File(path).isFile) }
        @Implementation fun extractMetadata(key: Int): String? = when (key) {
            MediaMetadataRetriever.METADATA_KEY_DURATION -> "1000"
            MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION -> "90"
            MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH -> width
            else -> null
        }
        @Implementation fun release() { released++ }
        companion object { var width = "96"; var released = 0 }
    }
}
