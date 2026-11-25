package com.kigtts.next.ui.onboarding

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeTipPlacementTest {
    @Test fun movesBelowAnchorNearTopInsteadOfClampingAcrossIt() {
        assertEquals(IntOffset(12, 76), homeTipOffset(
            IntSize(290, 320), IntSize(240, 100), Rect(8f, 26f, 208f, 64f), 12, preferSide = false,
        ))
    }

    @Test fun placesBesideFullHeightDragAreaInLandscape() {
        assertEquals(IntOffset(348, 130), homeTipOffset(
            IntSize(744, 360), IntSize(240, 100), Rect(600f, 0f, 654f, 360f), 12, preferSide = true,
        ))
    }

    @Test fun doesNotPlaceWhenCompactViewportHasNoSafeSlot() {
        assertNull(homeTipOffset(
            IntSize(300, 240), IntSize(270, 130), Rect(12f, 96f, 288f, 138f), 12, preferSide = false,
        ))
    }

    @Test fun waitsForTheAnchorToEnterTheViewport() {
        assertNull(homeTipOffset(
            IntSize(360, 500), IntSize(260, 90), Rect(32f, 520f, 120f, 550f), 12, preferSide = false,
        ))
    }
}
