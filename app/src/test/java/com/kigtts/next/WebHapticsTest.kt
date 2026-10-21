package com.kigtts.next
import com.kigtts.next.media.WebHapticGate
import org.junit.Test
import org.junit.Assert.*
class WebHapticsTest {
    @Test fun settingGestureRateAndTypeAreAllRequired() {
        val gate = WebHapticGate()
        assertFalse(gate.accept("click", true, true, 100)); gate.touched(100)
        assertFalse(gate.accept("click", false, true, 101)); assertFalse(gate.accept("click", true, false, 102))
        assertTrue(gate.accept("confirm", true, true, 103)); assertFalse(gate.accept("click", true, true, 120))
        assertFalse(gate.accept("pattern:1000", true, true, 200)); assertTrue(gate.accept("selection", true, true, 201))
        assertFalse(gate.accept("click", true, true, 1800)); gate.touched(1800); assertTrue(gate.accept("toggle-off", true, true, 1801))
    }
}
