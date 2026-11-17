package com.kigtts.next

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.kigtts.next.state.*
import org.junit.Assert.*
import org.junit.Test

class VoiceInputGestureTest {
    @Test fun releaseRemembersTheFinalZoneEvenBeforeTheColdSessionOpens() {
        val gesture = VoiceInputGestureState()
        gesture.area(VoiceReleaseTarget.CANCEL, Rect(0f, 400f, 140f, 490f))
        gesture.area(VoiceReleaseTarget.CONTINUOUS, Rect(150f, 400f, 300f, 490f))
        gesture.down(Offset(150f, 650f)); gesture.arm()
        gesture.move(Offset(90f, 450f)); assertEquals(VoiceReleaseTarget.CANCEL, gesture.selected)
        gesture.move(Offset(220f, 450f)); gesture.release()
        assertFalse(gesture.touching); assertFalse(gesture.armed)
        assertEquals(VoiceReleaseTarget.CONTINUOUS, gesture.consumeRelease())
        assertNull(gesture.consumeRelease())
    }
    @Test fun ordinaryTapsNeverSendAndRemovedReadingZoneDoesNotKeepItsOldAction() {
        val gesture = VoiceInputGestureState()
        gesture.down(Offset(100f, 650f)); gesture.release(); assertNull(gesture.pendingRelease)
        gesture.area(VoiceReleaseTarget.READ, Rect(0f, 400f, 100f, 490f))
        gesture.removeArea(VoiceReleaseTarget.READ)
        gesture.down(Offset(100f, 650f)); gesture.arm(); gesture.move(Offset(50f, 450f)); gesture.release()
        assertEquals(VoiceReleaseTarget.SEND, gesture.consumeRelease())
        gesture.down(Offset(100f, 650f)); gesture.arm(); gesture.cancel()
        assertEquals(VoiceReleaseTarget.CANCEL, gesture.consumeRelease())
    }
    @Test fun earlyReleaseSurvivesAnUnrelatedTapAndStationaryHoldDefaultsToSend() {
        val gesture = VoiceInputGestureState()
        gesture.area(VoiceReleaseTarget.CANCEL, Rect(0f, 0f, 200f, 200f))
        gesture.down(Offset(100f, 100f), 10f); gesture.arm()
        gesture.move(Offset(104f, 103f)); gesture.release()
        gesture.down(Offset(200f, 200f)); gesture.release()
        assertEquals(VoiceReleaseTarget.SEND, gesture.consumeRelease())
        assertNull(gesture.consumeRelease())
    }
}
