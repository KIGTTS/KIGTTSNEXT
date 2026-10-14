package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kigtts.next.theme.*
import com.kigtts.next.ui.components.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xhdpi")
class SnackbarMotionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun changingAnAnchorAndReversingItKeepsIntermediatePositionsWithoutAJump() {
        val host = SnackbarHostState(); val avoidance = SnackbarAvoidance()
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { AppSnackbarHost(host, avoidance) }
            LaunchedEffect(Unit) { host.showSnackbar("避让动画", duration = SnackbarDuration.Indefinite) }
        } }
        compose.waitForIdle(); compose.mainClock.autoAdvance = false
        fun bottom() = compose.onNodeWithTag("app-snackbar").getUnclippedBoundsInRoot().bottom.value
        fun step(ms: Long) {
            compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
            compose.mainClock.advanceTimeBy(ms); compose.waitForIdle()
        }
        val initial = bottom()
        compose.runOnIdle { avoidance.anchors["panel"] = 800f }
        step(16); val start = bottom()
        step(64); val moving = bottom()
        assertTrue(start <= initial && moving < start - 1f)
        assertTrue("Motion must not jump straight to the anchor", moving > 410f)
        compose.runOnIdle { avoidance.anchors.clear() }
        assertEquals("Retargeting keeps the current position", moving, bottom(), .5f)
        step(64); assertTrue(bottom() < initial - 1f)
        step(1200); assertEquals(initial, bottom(), 1f)
        compose.runOnIdle { avoidance.anchors["panel"] = 800f }
        step(1200); assertEquals(392f, bottom(), 1f)
    }
}
