package com.kigtts.next

import com.kigtts.next.state.PluginControlQueue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.Assert.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PluginControlQueueTest {
    @Test fun aBusyWorkerReceivesFinalSliderAndOtherControlsInOrder() = runTest {
        val gate = CompletableDeferred<Unit>()
        val received = mutableListOf<Triple<String, String, Map<String, String>>>()
        val queue = PluginControlQueue(this) { plugin, action, inputs ->
            received += Triple(plugin, action, inputs)
            if (received.size == 1) gate.await()
        }
        queue.submit("led", "size", mapOf("value" to "12"))
        advanceTimeBy(51); runCurrent()
        repeat(100) { queue.submit("led", "size", mapOf("value" to it.toString())) }
        queue.submit("led", "color", mapOf("value" to "4294967295"))
        assertEquals(1, received.size)
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(listOf("size", "size", "color"), received.map { it.second })
        assertEquals("99", received[1].third["value"])
    }
}
