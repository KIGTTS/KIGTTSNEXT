package com.kigtts.next

import com.kigtts.next.camera.expressions.FaceConfirmation
import org.junit.Assert.assertEquals
import org.junit.Test

class FaceConfirmationTest {
    @Test fun requiresSeparateFramesAndEnoughElapsedTime() {
        val confirmation = FaceConfirmation()
        assertEquals(emptySet<Long>(), confirmation.update(listOf(7), 1_000))
        assertEquals(emptySet<Long>(), confirmation.update(listOf(7), 1_000))
        assertEquals(emptySet<Long>(), confirmation.update(listOf(7), 1_105))
        assertEquals(setOf(7L), confirmation.update(listOf(7), 1_121))
    }

    @Test fun occasionalMissKeepsIdentityButStaleDetectionsMustStartOver() {
        val confirmation = FaceConfirmation()
        confirmation.update(listOf(1), 1_000)
        assertEquals(setOf(1L), confirmation.update(listOf(1, 2), 1_140))
        confirmation.update(emptyList(), 1_230)
        assertEquals(setOf(1L), confirmation.update(listOf(1), 1_250))
        assertEquals(emptySet<Long>(), confirmation.update(listOf(1, 2), 1_650))
    }
}
