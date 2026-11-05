package com.kigtts.next.ui.cards.camera

import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CameraVisitStateTest {
    @Test fun galleryStartsEmptyAndEarlierVisitsCannotPopulateIt() {
        val oldVisit = CameraVisitGalleryPreview()
        val pendingOldPhoto = oldVisit.capture()
        val visit = CameraVisitGalleryPreview()
        assertNull(visit.uri)
        pendingOldPhoto(Uri.parse("content://media/images/old"))
        assertNull(visit.uri)
        val photo = Uri.parse("content://media/images/new")
        visit.capture()(photo)
        assertEquals(photo, visit.uri)
        assertNull(CameraVisitGalleryPreview().uri)
    }

    @Test fun photoAndVideoReceiptsKeepLatestCaptureWhenSavingOutOfOrder() {
        val visit = CameraVisitGalleryPreview()
        val delayedPhoto = visit.capture()
        val video = visit.capture()
        val videoUri = Uri.parse("content://media/video/42")
        assertNull(visit.uri)
        video(videoUri)
        delayedPhoto(Uri.parse("content://media/images/41"))
        assertEquals(videoUri, visit.uri)
        val burst = visit.capture()
        burst(Uri.parse("content://media/images/43"))
        burst(Uri.parse("content://media/images/44"))
        assertEquals(Uri.parse("content://media/images/44"), visit.uri)
    }

    @Test fun zoomRetainsRatioUntilHardwareRestoresItAndClampsNewCapabilities() {
        val zoom = CameraZoomState()
        zoom.attach(false, .6f, 10f)
        zoom.ratio = 3f
        assertEquals(3f, zoom.attach(false, .6f, 10f), .001f)
        zoom.observed(1f, .6f, 10f, restoring = true)
        assertEquals(3f, zoom.ratio, .001f)
        zoom.observed(3f, .6f, 10f, restoring = false)
        assertEquals(2f, zoom.attach(false, 1f, 2f), .001f)
        assertEquals(1f, zoom.attach(true, 1f, 4f), .001f)
        zoom.clear()
        assertEquals(1f, zoom.minimum, .001f)
        assertEquals(1f, zoom.maximum, .001f)
    }
}
