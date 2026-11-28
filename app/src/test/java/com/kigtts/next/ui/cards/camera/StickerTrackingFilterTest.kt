package com.kigtts.next.ui.cards.camera

import android.graphics.RectF
import com.kigtts.next.camera.expressions.ExpressionFace
import com.kigtts.next.camera.expressions.StickerTrackingFilter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StickerTrackingFilterTest {
    private val filter = StickerTrackingFilter()

    @Test fun filtersSmallLandmarkAndBoundsChangesButFollowsFastMovement() {
        val first = face(7, 0f)
        filter.update(listOf(first), 0)
        val jitter = filter.update(listOf(face(7, 2f)), 33).single()
        assertTrue(jitter.bounds.left in first.bounds.left..first.bounds.left + 1f)
        assertTrue(jitter.leftEye.x in first.leftEye.x..first.leftEye.x + 1f)

        val moving = face(7, 30f)
        val followed = filter.update(listOf(moving), 66).single()
        assertEquals(moving.leftEye.x, followed.leftEye.x, 6f)
    }

    @Test fun tracksFacesIndependentlyAndDropsHistoryAfterLossOrReset() {
        val original = face(7, 0f)
        filter.update(listOf(original), 0)
        val different = face(8, 2f)
        assertSame(different, filter.update(listOf(different, original), 33).first())
        filter.update(emptyList(), 300)
        val returned = face(7, 5f)
        assertSame(returned, filter.update(listOf(returned), 333).single())
        filter.reset()
        assertSame(original, filter.update(listOf(original), 366).single())
    }

    @Test fun suppressesNestedSmallFacesAndRetainsOtherSubjects() {
        val main = face(7, 0f)
        val eye = face(8, 0f).copy(bounds = RectF(109f, 100f, 150f, 148f))
        val ear = face(9, 0f).copy(bounds = RectF(212f, 57f, 242f, 101f))
        val neighbor = face(10, 0f).copy(bounds = RectF(209f, 107f, 291f, 197f))
        val comparable = face(11, 0f).copy(bounds = RectF(88f, 43f, 232f, 279f))
        assertEquals(listOf(main, neighbor, comparable), filter.update(listOf(eye, main, ear, neighbor, comparable), 0))
        assertSame(eye, filter.update(listOf(eye), 33).single())
    }

    @Test fun fadedParentDoesNotHideAnotherFace() {
        val main = face(7, 0f).copy(opacity = .2f)
        val small = face(8, 0f).copy(bounds = RectF(109f, 100f, 150f, 148f))
        assertEquals(listOf(main, small), filter.update(listOf(main, small), 0))
    }

    private fun face(id: Long, offset: Float): ExpressionFace {
        val base = ExpressionFace.SAMPLE
        return base.copy(id = id, bounds = RectF(base.bounds).apply { offset(offset, 0f) },
            leftEye = base.leftEye.copy(x = base.leftEye.x + offset))
    }
}
