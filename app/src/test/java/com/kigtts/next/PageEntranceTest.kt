package com.kigtts.next

import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.state.*
import com.kigtts.next.theme.*
import com.kigtts.next.ui.navigation.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class PageEntranceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val plane = Color(0xFF8BC5CD)
    private fun show(scene: PageScene): PageEntry {
        val entry = PageEntry(scene, 1)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    PageMotion(entry, PageRole.FOREGROUND, true, 0f, 0, 0f, {}) { Box(Modifier.fillMaxSize().background(plane)) }
                }
            }
        }
        return entry
    }
    private fun step(time: Long) { compose.mainClock.advanceTimeBy(time); compose.waitForIdle() }
    private fun color(name: String, fraction: Float = .5f): Int {
        val path = "build/previews/" + name + ".png"
        compose.onRoot().captureRoboImage(path)
        val bitmap = BitmapFactory.decodeFile(path)
        return bitmap.getPixel((bitmap.width * fraction).toInt(), bitmap.height / 2)
    }
    private fun enter(scene: PageScene, direction: Float, name: String) {
        val entry = show(scene)
        val layer = compose.onNodeWithTag("page-layer/" + entry.identity)
        var previous = 393f
        repeat(13) { index ->
            step(32)
            val left = layer.getUnclippedBoundsInRoot().left.value
            assertTrue("Entry never crosses its resting position", left * direction >= -.1f)
            assertTrue("Entry stays monotonic", abs(left) <= previous + .1f)
            previous = abs(left)
            if (index == 2 && direction > 0) assertEquals("Entry stays unshaded", plane.toArgb(), color(name + "-mid", .98f))
        }
        assertEquals(0f, layer.getUnclippedBoundsInRoot().left.value, .1f)
        assertEquals(plane.toArgb(), color(name + "-complete"))
        compose.mainClock.autoAdvance = true
    }
    @Test fun rightHandSecondaryEntranceIsMonotonicWithoutGrayFlash() =
        enter(PageScene.Screen(Destination.Settings), 1f, "page-eased-entry-right")
    @Test fun leftHandSecondaryEntranceStaysMonotonicWithoutOvershoot() =
        enter(PageScene.Side(SidePage.SKETCH), -1f, "page-eased-entry-left")
    @Test fun restoredForegroundDoesNotRepeatItsEntrance() {
        val restoration = StateRestorationTester(compose)
        val entry = PageEntry(PageScene.Screen(Destination.Settings), 1)
        compose.mainClock.autoAdvance = false
        restoration.setContent { KigttsTheme(ThemeSettings()) {
            PageMotion(entry, PageRole.FOREGROUND, true, 0f, 0, 0f, {}) { Box(Modifier.fillMaxSize().background(plane)) }
        } }
        step(500)
        val layer = compose.onNodeWithTag("page-layer/" + entry.identity)
        assertEquals(0f, layer.getUnclippedBoundsInRoot().left.value, .1f)
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        assertEquals("Restoration begins at rest before any new animation frame", 0f, layer.getUnclippedBoundsInRoot().left.value, .1f)
        step(32)
        assertEquals(0f, layer.getUnclippedBoundsInRoot().left.value, .1f)
        compose.mainClock.autoAdvance = true
    }
}
