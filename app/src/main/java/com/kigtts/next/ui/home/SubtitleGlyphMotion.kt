package com.kigtts.next.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class SubtitleGlyphMotion {
    var frame by mutableStateOf<SubtitleGlyphFrame?>(null)
    var old: SubtitleGlyphFrame? = null
    var moves = emptyList<SubtitleGlyphMove>()
    var starts = emptyMap<Int, SubtitleGlyphPose>()
    var departures = emptyMap<Int, SubtitleGlyphPose>()
    private var hops = emptyMap<Int, SubtitleLineHop>()
    private var clock by mutableFloatStateOf(0f)
    private var started = 0f
    private var geometryOnly = false
    var job: Job? = null
    val progress get() = if (old == null) 1f else FastOutSlowInEasing.transform(((clock - started) / 170f).coerceIn(0f, 1f))
    val running get() = progress < 1f || hops.values.any { !it.finished(clock) }
    fun advanceBy(milliseconds: Float) { clock += milliseconds.coerceAtLeast(0f) }

    private fun target(frame: SubtitleGlyphFrame, index: Int): SubtitleGlyphPose {
        val bounds = frame.glyphs[index].bounds
        return SubtitleGlyphPose(bounds.topLeft + frame.origin, bounds.size)
    }
    fun outgoing(index: Int): SubtitleGlyphPose? = hops[index]?.outgoing(clock)
    fun pose(index: Int): SubtitleGlyphPose {
        val next = checkNotNull(frame)
        val target = target(next, index)
        val pose = hops[index]?.incoming(clock, target)
            ?: if (old == null) target else (starts[index] ?: target).toward(target, progress)
        return if (geometryOnly) pose.copy(size = target.size) else pose
    }

    fun update(next: SubtitleGlyphFrame, editStart: Int?, animate: Boolean, hopDistance: Float = 18f) {
        val before = frame
        if (before?.layout === next.layout && before.origin == next.origin && before.viewport == next.viewport) return
        val positions = before?.glyphs?.indices?.associateWith { outgoing(it) ?: pose(it) }.orEmpty()
        val layoutOnly = before?.layout?.layoutInput?.text == next.layout.layoutInput.text
        val permitted = animate && before != null && before.glyphs.size <= 240 && next.glyphs.size <= 240
        val edit = if (layoutOnly) null else editStart?.minus((next.layout.layoutInput.text.length - (before?.layout?.layoutInput?.text?.length ?: 0)).coerceAtLeast(0))
        val plan = if (before == null) emptyList() else subtitleGlyphMoves(before.glyphs, next.glyphs, edit)
        val relocated = if (before == null) emptySet() else subtitleRelocatedRows(before, next, plan)
        val rowCountChanged = before?.layout?.lineCount != next.layout.lineCount
        val nextHops = if (!permitted) emptyMap() else plan.mapNotNull { move ->
            val index = move.next ?: return@mapNotNull null
            val previous = move.old ?: return@mapNotNull null
            val retained = hops[previous]?.takeUnless { it.finished(clock) }
            val direction = if (next.glyphs[index].line > before!!.glyphs[previous].line) 1f else -1f
            val hop = if (move.changesLine && before.glyphs[previous].line !in relocated && (retained == null || retained.direction != direction))
                SubtitleLineHop(positions.getValue(previous), direction, clock, hopDistance) else retained
            hop?.let { index to it }
        }.toMap()
        if (!layoutOnly || !permitted || rowCountChanged) {
            departures = positions
            starts = plan.mapNotNull { move -> move.next?.let { index ->
                val end = target(next, index)
                index to (move.old?.let(positions::get) ?: end.copy(position = end.position + Offset(0f, end.size.height * .12f),
                    size = end.size * .94f, alpha = 0f))
            } }.toMap()
            moves = plan
            old = before.takeIf { permitted }
            started = clock
        } else if (progress >= 1f) old = null
        geometryOnly = layoutOnly
        // Geometry-only updates use the current paragraph immediately. Existing line
        // hops keep their clock across pinch/viewport updates instead of restarting.
        hops = nextHops
        frame = next
    }
}

@Composable
internal fun rememberSubtitleGlyphMotion(layout: TextLayoutResult?, width: Int, height: Int, alignment: Alignment,
    editStart: Int?, animate: Boolean): SubtitleGlyphMotion {
    val motion = remember { SubtitleGlyphMotion() }
    val direction = LocalLayoutDirection.current
    val hopDistance = with(LocalDensity.current) { 18.dp.toPx() }
    val scope = rememberCoroutineScope()
    SideEffect {
        if (layout != null) {
            val viewport = IntSize(width, height)
            val origin = alignment.align(layout.size, viewport, direction)
            if (motion.frame?.layout !== layout || motion.frame?.origin != Offset(origin.x.toFloat(), origin.y.toFloat()) || motion.frame?.viewport != viewport) {
                motion.update(subtitleGlyphFrame(layout, origin, viewport), editStart, animate, hopDistance)
                if (motion.running && motion.job?.isActive != true) motion.job = scope.launch {
                    var previous = 0L
                    while (motion.running) withFrameNanos { now ->
                        if (previous != 0L) motion.advanceBy((now - previous) / 1_000_000f)
                        previous = now
                    }
                }
            }
        }
    }
    return motion
}
