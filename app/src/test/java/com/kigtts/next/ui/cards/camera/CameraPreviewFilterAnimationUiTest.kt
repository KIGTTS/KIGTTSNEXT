package com.kigtts.next.ui.cards.camera

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLooper
import kotlin.math.abs

/** Observes the same matrix stream as the camera session; no desktop EGL pixel claim. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35])
class CameraPreviewFilterAnimationUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private data class Sample(val time: Long, val matrix: List<Float>) {
        // The B&W green contribution is zero in Original and .715 in full B&W.
        val monochromeAmount get() = matrix[1] / .715f
    }
    private class Fixture(initial: PhotoFilter, initialStrength: Float) {
        var filter by mutableStateOf(initial)
        var strength by mutableFloatStateOf(initialStrength)
        lateinit var state: CameraPreviewFilter
        val instances = mutableSetOf<CameraPreviewFilter>()
        val samples = mutableListOf<Sample>()
        var collectorStarts = 0
    }

    private fun observe(initial: PhotoFilter = PhotoFilter.ORIGINAL, strength: Float = 1f): Fixture {
        val fixture = Fixture(initial, strength)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val state = rememberCameraPreviewFilter(fixture.filter, fixture.strength)
            SideEffect { fixture.state = state; fixture.instances += state }
            // Its lifetime is the preview, rather than a filter or strength selection.
            LaunchedEffect(state) {
                fixture.collectorStarts++
                snapshotFlow { state.values().toList() }.collect {
                    fixture.samples += Sample(compose.mainClock.currentTime, it)
                }
            }
            Box(Modifier.size(1.dp).testTag("live-filter-observer"))
        }
        compose.waitForIdle()
        assertEquals(1, fixture.collectorStarts)
        assertEquals(1, fixture.samples.size)
        return fixture
    }

    private fun frame() {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        ShadowLooper.idleMainLooper()
    }

    private fun select(fixture: Fixture, filter: PhotoFilter = fixture.filter, strength: Float = fixture.strength) {
        val target = PhotoFilters.coefficients(filter, strength)
        compose.runOnIdle {
            fixture.filter = filter; fixture.strength = strength
            Snapshot.sendApplyNotifications()
        }
        // A paused Android looper must receive ApplyChanges before sampling an animation.
        var frames = 0
        while (!fixture.state.target.contentEquals(target) && frames++ < 8) frame()
        assertArrayEquals("The real effect must receive the new selection", target, fixture.state.target, .00001f)
    }

    private fun trace(fixture: Fixture) = "clock=${compose.mainClock.currentTime}, animation=${fixture.state.clock.value}, " +
        "running=${fixture.state.clock.isRunning}, current=${fixture.state.values()[1] / .715f}, " +
        "samples=" + fixture.samples.joinToString { "${it.time}:${it.monochromeAmount}" }

    private fun assertContinuous(fixture: Fixture) {
        assertEquals("Changing a filter must keep the same observer", 1, fixture.collectorStarts)
        assertEquals("Changing a filter must keep the same remembered preview state", 1, fixture.instances.size)
        assertTrue("The single collector must observe intermediate frames", fixture.samples.size >= 8)
        fixture.samples.forEach { sample ->
            assertTrue("Amount outside the Original/B&W range at ${sample.time}: ${sample.monochromeAmount}",
                sample.monochromeAmount in -.00001f..1.00001f)
            assertEquals(1f, sample.matrix[18], .00001f)
        }
        val original = PhotoFilters.coefficients(PhotoFilter.ORIGINAL)
        val monochrome = PhotoFilters.coefficients(PhotoFilter.BW)
        val coefficientSpan = original.indices.maxOf { abs(monochrome[it] - original[it]) }
        // FastOutSlowIn is cubic Bezier(.4,0,.2,1): x'(t) >= .525 and
        // y'(t) <= 1.5, so its slope is bounded by 20/7. With a 160ms tween
        // and the .787 maximum coefficient span, 16ms can change up to .224857.
        // This rejects an instantaneous full-preset jump and also requires two
        // observations at the very same frame time to have the same matrix.
        val easingSlopeBound = 1.5f / .525f
        fixture.samples.zipWithNext().forEach { (before, after) ->
            val elapsed = (after.time - before.time).coerceAtLeast(0L)
            val allowedChange = coefficientSpan * easingSlopeBound * elapsed / 160f
            val change = after.matrix.indices.maxOf { abs(after.matrix[it] - before.matrix[it]) }
            assertTrue("Matrix jumped at ${before.time}->${after.time}: $change, bound=$allowedChange",
                change <= allowedChange + .00001f)
        }
    }

    @Test fun aQuickMonochromeToOriginalReversalKeepsTheObservedMatrixContinuousAndSettles() {
        val fixture = observe()
        try {
            select(fixture, PhotoFilter.BW)
            repeat(4) { frame() }
            val turning = fixture.samples.last().monochromeAmount
            assertTrue("B&W must animate before the reversal: $turning; ${trace(fixture)}", turning in .1f.. .95f)
            select(fixture, PhotoFilter.ORIGINAL)
            // ApplyChanges may consume one more frame of the old trajectory. Compare
            // the reversal against the actual painted source accepted by the effect.
            val reversalSource = fixture.state.from[1] / .715f
            repeat(4) { frame() }
            val returning = fixture.samples.last().monochromeAmount
            assertTrue("Returning must pass through a visible intermediate matrix: $returning",
                returning > .001f && returning < reversalSource)
            repeat(14) { frame() }
            assertArrayEquals(PhotoFilters.coefficients(PhotoFilter.ORIGINAL), fixture.samples.last().matrix.toFloatArray(), .00001f)
            assertFalse("The reversal must settle instead of leaving the observer running", fixture.state.clock.isRunning)
            assertContinuous(fixture)
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun rapidStrengthChangesReuseOneCollectorAndFinishAtTheLatestStrength() {
        val fixture = observe(PhotoFilter.BW)
        try {
            select(fixture, strength = .25f)
            repeat(3) { frame() }
            val reduced = fixture.samples.last().monochromeAmount
            assertTrue("Reduced strength must still be moving: $reduced; ${trace(fixture)}", reduced > .25f && reduced < .99f)
            select(fixture, strength = 1f)
            val increasingSource = fixture.state.from[1] / .715f
            repeat(3) { frame() }
            val increased = fixture.samples.last().monochromeAmount
            assertTrue("Increasing strength must reverse the observed stream: $increased", increased > increasingSource && increased < 1f)
            select(fixture, strength = .15f)
            val decreasingSource = fixture.state.from[1] / .715f
            repeat(2) { frame() }
            val changedAgain = fixture.samples.last().monochromeAmount
            assertTrue("The third selection must start before the next one", changedAgain > .15f && changedAgain < decreasingSource)
            select(fixture, strength = 0f)
            repeat(14) { frame() }
            assertArrayEquals(PhotoFilters.coefficients(PhotoFilter.BW, 0f), fixture.samples.last().matrix.toFloatArray(), .00001f)
            assertFalse(fixture.state.clock.isRunning)
            assertContinuous(fixture)
        } finally { compose.mainClock.autoAdvance = true }
    }
}
