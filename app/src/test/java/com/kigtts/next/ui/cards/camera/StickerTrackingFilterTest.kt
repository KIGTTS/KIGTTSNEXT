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

    private fun face(id: Long, offset: Float): ExpressionFace {
        val base = ExpressionFace.SAMPLE
        return base.copy(id = id, bounds = RectF(base.bounds).apply { offset(offset, 0f) },
            leftEye = base.leftEye.copy(x = base.leftEye.x + offset))
    }
}
