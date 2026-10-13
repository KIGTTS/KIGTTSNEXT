package com.kigtts.next.ui.cards.camera

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLooper

/** The stream protocol/animation is observable without pretending to run a real camera GPU. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35])
class CameraStreamTransitionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private class Fixture {
        val blue = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        val green = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.GREEN) }
        var next: Bitmap? = blue
        var captures = 0
        lateinit var transition: CameraStreamTransition
        lateinit var scope: CoroutineScope
    }
    private fun show(): Fixture {
        val fixture = Fixture()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val context = LocalContext.current
            val transition = remember { CameraStreamTransition(PreviewView(context)) { fixture.captures++; fixture.next } }
            val scope = rememberCoroutineScope()
            SideEffect { fixture.transition = transition; fixture.scope = scope }
            DisposableEffect(transition) { onDispose { transition.close() } }
            Box(Modifier.size(200.dp).background(Color.Black).testTag("stream-transition-preview")) {
                transition.snapshot?.let { image ->
                    Image(image.asImageBitmap(), null, Modifier.fillMaxSize().graphicsLayer { alpha = transition.alpha.value })
                }
            }
        }
        compose.waitForIdle()
        return fixture
    }
    private fun frame() {
        compose.mainClock.advanceTimeByFrame(); compose.waitForIdle(); ShadowLooper.idleMainLooper()
    }
    private fun cover(fixture: Fixture): Job = compose.runOnIdle {
        fixture.scope.launch(start = CoroutineStart.UNDISPATCHED) { fixture.transition.cover() }
    }
    private fun reveal(fixture: Fixture): Job = compose.runOnIdle {
        fixture.scope.launch(start = CoroutineStart.UNDISPATCHED) { fixture.transition.reveal() }
    }

    @Test fun theOldFrameFadesInOnceAndRemainsUntilTheNewStreamFadesItOut() {
        val fixture = show()
        try {
            val covering = cover(fixture)
            assertSame(fixture.blue, fixture.transition.snapshot)
            assertEquals(1, fixture.captures)
            repeat(3) { frame() }
            val entering = fixture.transition.alpha.value
            assertTrue("The cover must fade in rather than jump: $entering", entering > 0f && entering < 1f)
            repeat(7) { frame() }
            assertTrue(covering.isCompleted)
            assertEquals(1f, fixture.transition.alpha.value, .00001f)
            repeat(5) { frame() }
            assertSame("No new streamed frame means the old cover stays visible", fixture.blue, fixture.transition.snapshot)
            assertEquals(1, fixture.captures)
            reveal(fixture)
            frame()
            assertEquals("The replacement layout gets two frames before fading", 1f, fixture.transition.alpha.value, .00001f)
            repeat(6) { frame() }
            val leaving = fixture.transition.alpha.value
            assertTrue("The cover must fade out after readiness: $leaving", leaving > 0f && leaving < 1f)
            assertSame(fixture.blue, fixture.transition.snapshot)
            repeat(15) { frame() }
            assertEquals(0f, fixture.transition.alpha.value, .00001f)
            assertNull(fixture.transition.snapshot)
            assertEquals(1, fixture.captures)
            assertFalse("The draw consumer may retain the bitmap", fixture.blue.isRecycled)
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun aSecondSwitchDuringRevealRetainsItsOwnCoverAndContinuesTheCurrentAlpha() {
        val fixture = show()
        try {
            cover(fixture); repeat(9) { frame() }
            val firstReveal = reveal(fixture)
            repeat(7) { frame() }
            val before = fixture.transition.alpha.value
            assertTrue(before > 0f && before < 1f)
            compose.runOnIdle { fixture.next = fixture.green }
            cover(fixture)
            assertEquals("A reversal must continue from the painted opacity", before, fixture.transition.alpha.value, .00001f)
            assertSame(fixture.green, fixture.transition.snapshot)
            repeat(10) { frame() }
            assertTrue(firstReveal.isCompleted)
            assertEquals(1f, fixture.transition.alpha.value, .00001f)
            assertSame("The old reveal must not clear the newer switch", fixture.green, fixture.transition.snapshot)
            assertEquals(2, fixture.captures)
            reveal(fixture); repeat(22) { frame() }
            assertNull(fixture.transition.snapshot)
            assertEquals(0f, fixture.transition.alpha.value, .00001f)
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun cancellationAndCloseKeepTheHostAliveAndNeverRecycleAPublishedFrame() {
        val fixture = show()
        try {
            val cancelled = cover(fixture)
            repeat(3) { frame() }
            compose.runOnIdle { cancelled.cancel() }
            frame()
            assertSame("Cancellation cannot uncover a camera already being replaced", fixture.blue, fixture.transition.snapshot)
            compose.runOnIdle { fixture.next = null }
            var hostContinued = false
            compose.runOnIdle {
                fixture.scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    fixture.transition.cover()
                    hostContinued = true
                }
            }
            assertSame("A temporarily empty preview reuses its last good frame", fixture.blue, fixture.transition.snapshot)
            compose.runOnIdle { fixture.transition.close(); fixture.transition.close() }
            val closedAlpha = fixture.transition.alpha.value
            repeat(12) { frame() }
            assertTrue("Closing cancels only the helper's animation, not the host coroutine", hostContinued)
            assertEquals(closedAlpha, fixture.transition.alpha.value, .00001f)
            assertNull(fixture.transition.snapshot)
            cover(fixture); reveal(fixture); repeat(4) { frame() }
            assertEquals(2, fixture.captures)
            assertFalse(fixture.blue.isRecycled); assertFalse(fixture.green.isRecycled)
        } finally { compose.mainClock.autoAdvance = true }
    }
}
