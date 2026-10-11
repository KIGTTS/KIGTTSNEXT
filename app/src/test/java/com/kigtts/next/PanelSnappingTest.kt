package com.kigtts.next

import androidx.compose.ui.hapticfeedback.*
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.home.*
import org.junit.Test
import org.junit.Assert.*

class PanelSnappingTest {
    @Test fun tallPortraitStopsContainWholeRowsAndIgnoreArbitraryRestoredHeights() {
        val geometry = PortraitPanelGeometry(900f)
        assertEquals(listOf(72f,184f,276f,368f,460f,552f,644f),geometry.anchors)
        assertEquals(276f,geometry.settle(300f,0f))
        assertEquals(368f,geometry.settle(300f,-700f))
        assertEquals(276f,geometry.settle(300f,700f))
        assertEquals(552f,geometry.settle(510f,0f))
        assertEquals(460f,geometry.settle(375f,-700f))
        assertEquals(368f,geometry.settle(450f,700f))
        assertEquals(552f,PortraitPanelGeometry(852f).maximum)
        assertEquals(276f,PortraitPanelGeometry(584f).maximum)
        assertEquals(184f,PortraitPanelGeometry(400f).maximum)
    }
    @Test fun ticksFollowRowAndColumnStopsWithNoDistanceTicksOrJitterDuplicates() {
        listOf(PortraitPanelGeometry(900f).anchors,LandscapePanelGeometry(600f).anchors).forEach { anchors ->
            val ticks=mutableListOf<HapticFeedbackType>()
            val drag=DragFeedback(object:HapticFeedback { override fun performHapticFeedback(hapticFeedbackType:HapticFeedbackType) { ticks+=hapticFeedbackType } })
            val snap=AnchorFeedback(drag);val from=anchors[1];val target=anchors[2]
            drag.start(from);snap.start(from,anchors)
            snap.move(from+20f,anchors);snap.move((from+target)/2,anchors)
            assertEquals(listOf(HapticFeedbackType.GestureThresholdActivate),ticks)
            snap.move(target-3f,anchors);snap.move(target+2f,anchors);snap.move(target-2f,anchors)
            assertEquals(1,ticks.count { it==HapticFeedbackType.SegmentFrequentTick })
            snap.move(target-24f,anchors);snap.move(target,anchors)
            assertEquals(2,ticks.count { it==HapticFeedbackType.SegmentFrequentTick })
            drag.finish();assertEquals(1,ticks.count { it==HapticFeedbackType.GestureEnd })
        }
    }
    @Test fun canceledReorderHasPickupAndStepsButNoCompletionPulse() {
        val ticks=mutableListOf<HapticFeedbackType>()
        val feedback=DragFeedback(object:HapticFeedback { override fun performHapticFeedback(hapticFeedbackType:HapticFeedbackType) { ticks+=hapticFeedbackType } })
        val item=com.kigtts.next.ui.components.reorder.ReorderTarget("a",androidx.compose.ui.geometry.Rect(0f,0f,100f,100f))
        val drag=com.kigtts.next.ui.components.reorder.ReorderDrag({listOf(item)},{_,_->},feedback=feedback)
        drag.begin(item,androidx.compose.ui.geometry.Offset(10f,10f));feedback.step();drag.finish(cancelled=true)
        assertEquals(listOf(HapticFeedbackType.GestureThresholdActivate,HapticFeedbackType.SegmentFrequentTick),ticks)
    }
}
