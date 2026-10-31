package com.kigtts.next.ui.cards.camera

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED) @Config(sdk = [35], qualifiers = "zh-rCN-w852dp-h852dp-xhdpi")
class CameraControlGroupUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun check(vertical: Boolean) {
        val clicks = IntArray(5)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Box(Modifier.size(if (vertical) 700.dp else 360.dp, if (vertical) 300.dp else 640.dp)) {
                CameraOverlayControls(PolaroidFormat.SQUARE, listOf(PolaroidFormat.SQUARE), PhotoFilter.ORIGINAL,
                    true, vertical, false, { clicks[3]++ }, { clicks[4]++ }, { clicks[1]++ }, {},
                    flashAvailable = true, cycleFlash = { clicks[0]++ }, motionButton = {
                        CameraMotionButton(false, true, true, false, true, { clicks[2]++ })
                    })
                CameraCornerToolbar(true, false, true, false, {}, {}, applyInsets = false,
                    vertical = vertical, showSettings = false)
            }
        } }
        val tags = listOf("camera-flash", "camera-filter-toggle", "camera-motion", "camera-format", "camera-settings")
        val boxes = tags.map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
        boxes.zipWithNext().forEach { (a, b) ->
            if (vertical) { assertEquals(a.left, b.left, 1f); assertTrue(b.top - a.bottom in 0f..8f) }
            else { assertEquals(a.top, b.top, 1f); assertTrue(b.left - a.right in 0f..8f) }
        }
        val back = compose.onNodeWithTag("camera-cancel").fetchSemanticsNode().boundsInRoot
        boxes.forEach { assertFalse("Back must have its own corner", it.overlaps(back)) }
        if (vertical) {
            val strip = compose.onNodeWithTag("camera-switch-strip").fetchSemanticsNode().boundsInRoot
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            assertTrue("Landscape controls belong at the bottom left", strip.bottom > root.bottom * .9f)
            assertTrue("Back stays at the top left", back.right < root.width * .2f && back.bottom < strip.top)
        }
        tags.forEach { compose.onNodeWithTag(it).performTouchInput { click() } }
        assertArrayEquals(intArrayOf(1, 1, 1, 1, 1), clicks)
        compose.onRoot().captureRoboImage("build/previews/camera-connected-${if (vertical) "landscape" else "portrait"}.png")
    }
    @Test fun portraitGroupsFiveActionsAtTheTopRight() = check(false)
    @Test fun landscapeGroupsFiveActionsAtTheBottomLeft() = check(true)
}
