package com.kigtts.next

import com.kigtts.next.ui.home.ListeningFollowState
import org.junit.Assert.*
import org.junit.Test

class ListeningFollowTest {
    @Test fun growingWrappedTextKeepsFollowEvenBeforeOffsetCatchesUp() {
        val state = ListeningFollowState(); state.observe(100, 100); state.observe(100, 140)
        assertTrue(state.following)
    }
    @Test fun userReadingHistoryIsNotInterruptedByPartialUpdates() {
        val state = ListeningFollowState(); state.observe(100, 100)
        state.userMovedTowardHistory(); state.observe(50, 100); assertFalse(state.following)
        state.observe(50, 150); assertFalse(state.following)
        state.observe(150, 150); assertTrue(state.following)
        state.observe(150, 200); assertTrue(state.following)
    }
    @Test fun accessibilityOrWheelScrollTowardHistoryAlsoPausesFollow() {
        val state = ListeningFollowState(); state.observe(200, 200); state.observe(120, 200)
        assertFalse(state.following)
    }
    @Test fun layoutClampAndUnmeasuredExtentDoNotLookLikeUserHistoryScroll() {
        val state = ListeningFollowState(); state.observe(100, 100); state.observe(60, 60)
        assertTrue(state.following)
        state.observe(60, Int.MAX_VALUE); assertTrue(state.following)
    }
}
