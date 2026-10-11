package com.kigtts.next.ui.components

import kotlin.math.abs

/** Tick at real snap points; ignore pointer jitter while the finger stays near the same point. */
class AnchorFeedback(private val feedback: DragFeedback) {
    private var previous = 0f
    private var last: Float? = null
    fun start(value: Float, anchors: List<Float>) {
        previous = value
        last = anchors.minByOrNull { abs(it - value) }?.takeIf { abs(it - value) <= 12f }
    }
    fun move(value: Float, anchors: List<Float>) {
        val crossed = anchors.filter { point ->
            if (value > previous) point > previous && point <= value else point < previous && point >= value
        }.let { if (value < previous) it.reversed() else it }
        val near = anchors.minByOrNull { abs(it - value) }?.takeIf { abs(it - value) <= 6f }
        (crossed + listOfNotNull(near)).distinct().forEach { point ->
            if (point != last) { feedback.step(); last = point }
        }
        if (last?.let { abs(it - value) > 12f } == true) last = null
        previous = value
    }
}
