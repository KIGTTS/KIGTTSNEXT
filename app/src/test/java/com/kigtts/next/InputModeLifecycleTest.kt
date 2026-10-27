package com.kigtts.next

import com.kigtts.next.ui.home.ImeInputSession
import org.junit.Assert.*
import org.junit.Test

class InputModeLifecycleTest {
    @Test fun closesInputAtStartOfKeyboardDismissalWhileKeyboardStillOccupiesTheWindow() {
        val session = ImeInputSession()
        assertFalse(session.update(true, false, false, 0, 0))
        assertFalse(session.update(true, false, true, 0, 900))
        assertFalse(session.update(true, false, true, 900, 900))
        assertTrue(session.update(true, false, true, 900, 0))
        assertFalse(session.update(true, false, true, 450, 0))
        assertFalse(session.update(true, false, false, 0, 0))
    }
    @Test fun dialogsAndFloatingKeyboardsDoNotPrematurelyEndEditing() {
        val session = ImeInputSession()
        assertFalse(session.update(true, false, true, 0, 0))
        assertFalse(session.update(true, false, true, 0, 0))
        assertFalse(session.update(true, true, false, 0, 0))
        assertFalse(session.update(true, false, false, 0, 0))
        assertFalse(session.update(true, false, true, 0, 0))
        assertTrue(session.update(true, false, false, 0, 0))
        assertFalse(session.update(false, false, false, 0, 0))
        assertFalse(session.update(true, false, true, 700, 700))
        assertTrue(session.update(true, false, false, 0, 0))
    }
}
