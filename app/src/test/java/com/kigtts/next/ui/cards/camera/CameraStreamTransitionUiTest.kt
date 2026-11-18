package com.kigtts.next.ui.cards.camera

import android.graphics.Bitmap
import android.view.Surface
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
        val blank = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLACK) }
        var next: Bitmap? = blue
        var captures = 0
        lateinit var transition: CameraStreamTransition
        lateinit var scope: CoroutineScope
    }
    private fun show(retained: CameraTransitionFrame? = null): Fixture {
        val fixture = Fixture()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val context = LocalContext.current
            val transition = remember { CameraStreamTransition(PreviewView(context), retained) { fixture.captures++; fixture.next } }
            val scope = rememberCoroutineScope()
            SideEffect { fixture.transition = transition; fixture.scope = scope }
            DisposableEffect(transition) { onDispose { transition.close() } }
            Box(Modifier.size(200.dp).background(Color.Black).testTag("stream-transition-preview")) {
                transition.frame?.bitmap?.let { image ->
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
    private fun cover(fixture: Fixture, rotation: Int? = Surface.ROTATION_0): Job = compose.runOnIdle {
        fixture.scope.launch(start = CoroutineStart.UNDISPATCHED) { fixture.transition.cover(rotation) }
    }
    private fun reveal(fixture: Fixture): Job = compose.runOnIdle {
        fixture.scope.launch(start = CoroutineStart.UNDISPATCHED) { fixture.transition.reveal(Surface.ROTATION_0) }
    }

    @Test fun theOldFrameFadesInOnceAndRemainsUntilTheNewStreamFadesItOut() {
        val fixture = show()
        try {
            val covering = cover(fixture)
            assertSame(fixture.blue, fixture.transition.frame?.bitmap)
            assertEquals(1, fixture.captures)
            repeat(3) { frame() }
            val entering = fixture.transition.alpha.value
            assertTrue("The cover must fade in rather than jump: $entering", entering > 0f && entering < 1f)
            repeat(7) { frame() }
            assertTrue(covering.isCompleted)
            assertEquals(1f, fixture.transition.alpha.value, .00001f)
            repeat(5) { frame() }
            assertSame("No new streamed frame means the old cover stays visible", fixture.blue, fixture.transition.frame?.bitmap)
            assertEquals(1, fixture.captures)
            reveal(fixture)
            frame()
            assertEquals("The replacement layout gets two frames before fading", 1f, fixture.transition.alpha.value, .00001f)
            repeat(6) { frame() }
            val leaving = fixture.transition.alpha.value
            assertTrue("The cover must fade out after readiness: $leaving", leaving > 0f && leaving < 1f)
            assertSame(fixture.blue, fixture.transition.frame?.bitmap)
            repeat(15) { frame() }
            assertEquals(0f, fixture.transition.alpha.value, .00001f)
            assertNull(fixture.transition.frame)
            assertEquals(2, fixture.captures)
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
            assertSame(fixture.green, fixture.transition.frame?.bitmap)
            repeat(10) { frame() }
            assertTrue(firstReveal.isCompleted)
            assertEquals(1f, fixture.transition.alpha.value, .00001f)
            assertSame("The old reveal must not clear the newer switch", fixture.green, fixture.transition.frame?.bitmap)
            assertEquals(3, fixture.captures)
            reveal(fixture); repeat(22) { frame() }
            assertNull(fixture.transition.frame)
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
            assertSame("Cancellation cannot erase the saved camera frame", fixture.blue, fixture.transition.frame?.bitmap)
            compose.runOnIdle { fixture.next = null }
            var hostContinued = false
            compose.runOnIdle {
                fixture.scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    fixture.transition.cover(null)
                    hostContinued = true
                }
            }
            assertSame("A temporarily empty preview reuses its last good frame", fixture.blue, fixture.transition.frame?.bitmap)
            compose.runOnIdle { fixture.transition.close(); fixture.transition.close() }
            val closedAlpha = fixture.transition.alpha.value
            repeat(12) { frame() }
            assertTrue("Closing cancels only the helper's animation, not the host coroutine", hostContinued)
            assertEquals(closedAlpha, fixture.transition.alpha.value, .00001f)
            assertNull(fixture.transition.frame)
            cover(fixture); reveal(fixture); repeat(4) { frame() }
            assertEquals(1, fixture.captures)
            assertFalse(fixture.blue.isRecycled); assertFalse(fixture.green.isRecycled)
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun blankFramesAndRepeatedSwitchesKeepTheMostRecentUsableFrame() {
        val fixture = show()
        try {
            reveal(fixture); repeat(4) { frame() }
            assertSame(fixture.blue, fixture.transition.lastGoodFrame?.bitmap)
            compose.runOnIdle { fixture.next = fixture.blank }
            cover(fixture); repeat(2) { frame() }
            assertSame(fixture.blue, fixture.transition.frame?.bitmap)
            repeat(3) {
                cover(fixture, null); frame()
                assertEquals(1f, fixture.transition.alpha.value, .00001f)
                assertSame(fixture.blue, fixture.transition.frame?.bitmap)
            }
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun aRecreatedCameraVisitStartsWithTheRetainedCoverAlreadyOpaque() {
        val image = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        val cache = CameraRotationFrames()
        val old = CameraTransitionFrame(image, Surface.ROTATION_90)
        cache.retain("camera", old)
        val fixture = show(cache.take("camera"))
        try {
            assertSame(old, fixture.transition.frame)
            assertEquals(1f, fixture.transition.alpha.value, .00001f)
            assertNull(cache.take("camera"))
            cache.retain("camera", old)
            assertNull(cache.take("unrelated-visit"))
            cover(fixture, null); repeat(8) { frame() }
            assertSame(old, fixture.transition.frame)
            assertEquals(0, fixture.captures)
            reveal(fixture); repeat(22) { frame() }
            assertNull(fixture.transition.frame)
            assertSame(fixture.blue, fixture.transition.lastGoodFrame?.bitmap)
        } finally { compose.mainClock.autoAdvance = true }
    }
}
