package com.kigtts.next

import com.kigtts.next.state.*
import org.junit.Assert.*
import org.junit.Test

class HomeExitTest {
    @Test fun firstUseAsksAndSingleBackLeavesWithoutArming() {
        val gate = HomeExitGate()
        assertEquals(HomeExitAction.ASK, gate.back(null, 100L))
        assertEquals(HomeExitAction.ASK, gate.back(null, 200L))
        assertEquals(HomeExitAction.LEAVE, gate.back(false, 300L))
        assertEquals(HomeExitAction.HINT, gate.back(true, 400L))
    }
    @Test fun onlyASecondBackWithinTwoSecondsLeavesAndResetPreventsStaleExit() {
        val gate = HomeExitGate()
        assertEquals(HomeExitAction.HINT, gate.back(true, 100L))
        assertEquals(HomeExitAction.HINT, gate.back(true, 2200L))
        assertEquals(HomeExitAction.LEAVE, gate.back(true, 2300L))
        assertEquals(HomeExitAction.HINT, gate.back(true, 2400L))
        gate.reset()
        assertEquals(HomeExitAction.HINT, gate.back(true, 2500L))
        assertEquals(HomeExitAction.LEAVE, gate.back(true, 4500L))
    }
}
