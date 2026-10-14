package com.kigtts.next

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.*
import com.kigtts.next.ui.components.DragFeedback
import com.kigtts.next.ui.components.reorder.*
import org.junit.Assert.*
import org.junit.Test

class ReorderSlotStabilityTest {
    @Test fun AnimatedNeighborsCannotRepeatedlySwapOrVibrateUnderAStationaryFinger() {
        val signals = mutableListOf<HapticFeedbackType>()
        val feedback = DragFeedback(object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { signals += hapticFeedbackType }
        })
        val a = ReorderTarget("a", Rect(0f, 0f, 100f, 100f))
        var b = ReorderTarget("b", Rect(100f, 0f, 200f, 100f))
        val c = ReorderTarget("c", Rect(200f, 0f, 300f, 100f))
        val swaps = mutableListOf<String>()
        val drag = ReorderDrag({ listOf(a, b, c) }, { _, to -> swaps += to; feedback.step() }, horizontalOnly = true, animateDrop = true, feedback = feedback)
        drag.begin(a, Offset(50f, 50f)); drag.drag(Offset(100f, 0f))
        repeat(120) { frame ->
            b = b.copy(bounds = Rect(100f + frame % 5, 0f, 200f + frame % 5, 100f))
            drag.checkTarget(); drag.drag(Offset.Zero)
        }
        assertEquals(listOf("b"), swaps)
        assertEquals(1, signals.count { it == HapticFeedbackType.SegmentFrequentTick })
        drag.drag(Offset(100f, 0f)); assertEquals(listOf("b", "c"), swaps)
        drag.drag(Offset(-100f, 0f)); assertEquals(listOf("b", "c", "b"), swaps)
        drag.finish(); repeat(20) { drag.checkTarget() }
        assertEquals(3, swaps.size); assertEquals(1, signals.count { it == HapticFeedbackType.GestureEnd })
    }

    @Test fun AutoscrollMovesTheAcceptedSlotSoFurtherRowsCanStillBeReached() {
        val a = ReorderTarget("a", Rect(0f, 0f, 100f, 100f))
        var target = ReorderTarget("b", Rect(0f, 100f, 100f, 200f))
        val swaps = mutableListOf<String>()
        val drag = ReorderDrag({ listOf(a, target) }, { _, to -> swaps += to }, verticalOnly = true)
        drag.begin(a, Offset(50f, 50f)); drag.drag(Offset(0f, 100f))
        target = ReorderTarget("c", Rect(0f, 100f, 100f, 200f))
        drag.viewportScrolled(120f, false); drag.checkTarget()
        assertEquals(listOf("b", "c"), swaps)
    }
}
