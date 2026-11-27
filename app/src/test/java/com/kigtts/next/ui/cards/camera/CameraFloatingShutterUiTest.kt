package com.kigtts.next.ui.cards.camera

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.kigtts.next.i18n.LocalUiLocale
import com.kigtts.next.theme.KigttsTheme
import com.kigtts.next.theme.ThemeMode
import com.kigtts.next.theme.ThemeSettings
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class CameraFloatingShutterUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun dragVersusTapRotatesAndDropsTheTemporaryShutterBackIntoTheDock() {
        var landscape by mutableStateOf(false)
        var captures = 0
        var bursting by mutableStateOf(false)
        var bursts = 0
        lateinit var shutterState: CameraFloatingShutterState
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.DARK)) {
            val shutter = rememberCameraFloatingShutter("test-visit")
            SideEffect { shutterState = shutter }
            val capture = { captures++; Unit }
            Box(Modifier.size(if (landscape) 390.dp else 280.dp, if (landscape) 240.dp else 520.dp)) {
                CameraPageLayout(preview = { modifier, _ -> Box(modifier) }, controls = { vertical ->
                    CameraActionBar(bursting, true, true, true, {}, capture, {}, vertical, floatingShutter = shutter,
                        burstStart = { bursting = true; bursts++ }, burstEnd = { bursting = false },
                        burstRunning = bursting, burstHeld = bursting)
                }) { vertical ->
                    CameraFloatingShutter(shutter, vertical, true, false, false, capture)
                }
            }
        } }
        val workspace = compose.onNodeWithTag("polaroid-camera")
        val floating = compose.onNodeWithTag("camera-floating-shutter")
        val dock = compose.onNodeWithTag("camera-shutter")
        fun position(node: SemanticsNodeInteraction): Offset = node.fetchSemanticsNode().boundsInRoot.center -
            workspace.fetchSemanticsNode().boundsInRoot.topLeft
        fun dragFrom(node: SemanticsNodeInteraction, target: Offset) {
            val from = position(node)
            workspace.performTouchInput { swipe(from, target, 250) }
        }

        floating.assertDoesNotExist()
        dock.performTouchInput { click() }
        assertEquals(1, captures)
        val region = workspace.fetchSemanticsNode().boundsInRoot
        val target = Offset(region.width * .32f, region.height * .57f)
        dragFrom(dock, target)
        floating.assertIsDisplayed()
        assertEquals(1, captures)
        assertTrue((position(floating) - target).getDistance() < 3f)

        val moved = Offset(region.width * .36f, region.height * .34f)
        dragFrom(floating, moved)
        assertTrue((position(floating) - moved).getDistance() < 3f)
        compose.runOnIdle { landscape = true }
        val rotated = workspace.fetchSemanticsNode().boundsInRoot
        val expected = Offset(rotated.width * .34f, rotated.height * .64f)
        assertTrue((position(floating) - expected).getDistance() < 3f)

        floating.performTouchInput { click() }
        assertEquals(2, captures)
        compose.runOnIdle { assertFalse(shutterState.dimmed) }
        compose.mainClock.advanceTimeBy(3_300)
        compose.waitUntil(5_000) { shutterState.dimmed }

        dragFrom(floating, position(dock))
        floating.assertDoesNotExist()
        assertEquals(2, captures)
        dock.performTouchInput { click() }
        assertEquals(3, captures)
        dock.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(700)
        compose.waitForIdle()
        assertEquals(1, bursts)
        floating.assertDoesNotExist()
        dock.performTouchInput { up() }
        compose.runOnIdle { assertFalse(bursting); assertEquals(3, captures) }
    }

    @Test fun volumeHintAppearsByTheUpperRightOnce() {
        val preferences = compose.activity.getSharedPreferences("camera-volume-hint-test", 0)
        preferences.edit().clear().commit()
        var visit by mutableIntStateOf(0)
        var eligible by mutableStateOf(false)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            CompositionLocalProvider(LocalUiLocale provides "en") {
                val options = remember(visit) { CameraOptions(preferences) }
                CameraPageLayout(preview = { modifier, _ -> Box(modifier) }, controls = { }) { vertical ->
                    CameraVolumeCoachMark(options, eligible, vertical, Surface.ROTATION_0)
                }
            }
        } }
        val hint = compose.onNodeWithTag("camera-volume-tip")
        hint.assertDoesNotExist()
        compose.runOnIdle { eligible = true }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(800)
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("camera-volume-tip").fetchSemanticsNodes().isNotEmpty() }
        val bounds = hint.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val camera = compose.onNodeWithTag("polaroid-camera").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.center.x > camera.center.x)
        assertTrue(bounds.center.y < camera.center.y)
        assertTrue(CameraOptions(preferences).volumeHintSeen())
        compose.onNodeWithText("Got it").performClick()
        hint.assertDoesNotExist()
        compose.runOnIdle { visit++ }
        compose.mainClock.advanceTimeBy(800)
        hint.assertDoesNotExist()
    }
}
