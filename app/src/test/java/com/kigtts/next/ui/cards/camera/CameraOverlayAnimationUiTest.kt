package com.kigtts.next.ui.cards.camera

import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.theme.*
import com.kigtts.plugin.api.PolaroidFormat
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class CameraOverlayAnimationUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun advance(milliseconds: Long) {
        compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
        compose.mainClock.advanceTimeBy(milliseconds); compose.waitForIdle()
    }

    @Test fun portraitFlashCapabilityChangesWithoutMovingTheConnectedGroup() = flashPresence(false)
    @Test fun landscapeFlashCapabilityChangesWithoutMovingTheConnectedGroup() = flashPresence(true)

    private fun flashPresence(vertical: Boolean) {
        var available by mutableStateOf(false)
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
            Box(Modifier.size(320.dp, 320.dp).background(androidx.compose.ui.graphics.Color.Black).testTag("overlay-root")) {
                CameraOverlayControls(PolaroidFormat.SQUARE, listOf(PolaroidFormat.MINI, PolaroidFormat.SQUARE),
                    PhotoFilter.ORIGINAL, true, vertical, false, {}, {}, {}, {}, flashAvailable = available)
            }
        } }
        compose.waitForIdle()
        compose.onNodeWithTag("camera-flash").assertDoesNotExist()
        fun extent(): Float = bounds("camera-switch-strip").let { if (vertical) it.height else it.width }
        val collapsed = extent()
        val adjacent = bounds("camera-settings")
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { available = true }
        advance(96)
        assertEquals("Other actions keep their positions while the lens reports flash capability", adjacent, bounds("camera-settings"))
        val entering = extent()
        compose.onNodeWithTag("camera-flash").assertExists()
        compose.onNodeWithTag("overlay-root").captureRoboImage("build/previews/camera-flash-${if (vertical) "landscape" else "portrait"}-enter.png")
        advance(600)
        val expanded = extent()
        assertTrue("The group grows continuously", entering > collapsed && entering < expanded)
        compose.runOnIdle { available = false }
        advance(96)
        assertTrue("The group shrinks continuously", extent() > collapsed && extent() < expanded)
        compose.runOnIdle { available = true }
        advance(600)
        compose.onNodeWithTag("camera-flash").assertIsEnabled()
        assertEquals(adjacent, bounds("camera-settings"))
        compose.runOnIdle { available = false }
        advance(600)
        compose.onNodeWithTag("camera-flash").assertDoesNotExist()
        assertEquals(collapsed, extent(), 1f)
    }

    @Test fun countdownKeepsCenteredRoundBackingThroughEntryTickCancelAndImmediateRestart() {
        var seconds by mutableIntStateOf(0)
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
            Box(Modifier.size(320.dp).background(androidx.compose.ui.graphics.Color.White).testTag("overlay-root")) {
                CameraCountdownOverlay(seconds)
            }
        } }
        compose.waitForIdle(); compose.mainClock.autoAdvance = false
        compose.runOnIdle { seconds = 10 }
        advance(96)
        val root = bounds("overlay-root")
        val center = Rect(root.center.x - 140f, root.center.y - 140f, root.center.x + 140f, root.center.y + 140f)
        assertRoundSilhouette("countdown-enter", center, dark = true)
        advance(600)
        assertDigitCentered("10", root)
        compose.runOnIdle { seconds = 9 }
        advance(80)
        assertRoundSilhouette("countdown-tick", center, dark = true)
        advance(600)
        compose.onNodeWithText("9").assertExists()
        assertDigitCentered("9", root)
        compose.runOnIdle { seconds = 0 }
        advance(64)
        assertRoundSilhouette("countdown-exit", center, dark = true)
        compose.runOnIdle { seconds = 3 }
        advance(96)
        assertRoundSilhouette("countdown-restart", center, dark = true)
        advance(600)
        compose.onNodeWithText("3").assertExists()
        assertDigitCentered("3", root)
        compose.onNodeWithText("0").assertDoesNotExist()
        compose.runOnIdle { seconds = 0 }
        advance(600)
        compose.onNodeWithText("3").assertDoesNotExist()
    }

    private fun assertDigitCentered(digit: String, root: Rect) {
        val text = compose.onNodeWithText(digit).fetchSemanticsNode().boundsInRoot
        assertEquals(root.center.x, text.center.x, 1f)
        assertEquals(root.center.y, text.center.y, 1f)
        assertTrue("The digit occupies its own bounds, not a full badge with top-left text", text.width < 250f)
    }

    /** Pixel symmetry detects a circular badge being wiped away by a changing rectangular clip. */
    private fun assertRoundSilhouette(name: String, region: Rect, dark: Boolean) {
        val root = bounds("overlay-root")
        val file = File("build/previews/camera-overlay-$name.png").apply { parentFile?.mkdirs() }
        compose.onNodeWithTag("overlay-root").captureRoboImage(file.absolutePath)
        val bitmap = requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
        try {
            val scale = bitmap.width / root.width
            val left = ((region.left - root.left) * scale).toInt().coerceAtLeast(0)
            val right = ((region.right - root.left) * scale).toInt().coerceAtMost(bitmap.width)
            val top = ((region.top - root.top) * scale).toInt().coerceAtLeast(0)
            val bottom = ((region.bottom - root.top) * scale).toInt().coerceAtMost(bitmap.height)
            var minX = right; var maxX = left; var minY = bottom; var maxY = top
            for (y in top until bottom) for (x in left until right) {
                val pixel = bitmap.getPixel(x, y)
                val present = if (dark) Color.red(pixel) < 250 else Color.red(pixel) > 10
                if (present) { minX = minOf(minX, x); maxX = maxOf(maxX, x); minY = minOf(minY, y); maxY = maxOf(maxY, y) }
            }
            assertTrue("Animated badge is still visible ($name)", maxX > minX && maxY > minY)
            assertTrue("Complete round silhouette ($name): ${maxX - minX} x ${maxY - minY}", abs((maxX - minX) - (maxY - minY)) <= 3)
            assertEquals("Horizontal centre stays fixed ($name)", ((region.center.x - root.left) * scale), (minX + maxX) / 2f, 2f)
            assertEquals("Vertical centre stays fixed ($name)", ((region.center.y - root.top) * scale), (minY + maxY) / 2f, 2f)
        } finally { bitmap.recycle() }
    }
}
