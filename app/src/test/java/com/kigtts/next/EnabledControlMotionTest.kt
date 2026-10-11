@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next

import androidx.activity.ComponentActivity
import android.graphics.BitmapFactory
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
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
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class EnabledControlMotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun step(ms: Long) { repeat(((ms + 15) / 16).toInt()) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() } }
    private fun color(tag: String, x: Float = .5f): Color {
        val path = "build/previews/enabled-$tag.png"
        compose.onNodeWithTag(tag).captureRoboImage(path)
        val pixels = BitmapFactory.decodeFile(path)
        return Color(pixels.getPixel((pixels.width * x).toInt(), pixels.height / 2)).also { pixels.recycle() }
    }
    private fun mixed(value: Color) { assertTrue("Expected transition color: $value", value.red > .01f && value.blue > .01f) }

    @Test fun disablingPressedButtonCancelsInputImmediatelyWhileColorsEaseAndRapidReversalsSettle() {
        val enabled = mutableStateOf(true); val source = MutableInteractionSource()
        var clicks = 0; var pressed = false
        compose.setContent { MaterialTheme {
            val actual = source.collectIsPressedAsState().value
            SideEffect { pressed = actual }
            ToolButton("action", Icons.Default.Menu, { clicks++ }, Modifier.testTag("action"), source,
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Blue,
                    disabledContainerColor = Color.Red, contentColor = Color.Transparent, disabledContentColor = Color.Transparent),
                enabled = enabled.value)
        } }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("action").performTouchInput { down(center) }; step(32); assertTrue(pressed)
        compose.runOnIdle { enabled.value = false }; step(32)
        compose.onNodeWithTag("action").assertIsNotEnabled(); step(16)
        assertFalse(pressed); mixed(color("action"))
        compose.onNodeWithTag("action").performTouchInput { up(); click() }
        assertEquals(0, clicks); step(500)
        // Material's state layer can tint the surface; the opposite channel must fully disappear.
        val disabled = color("action"); assertTrue(disabled.red > .85f); assertEquals(0f, disabled.blue, .01f)
        repeat(7) { compose.runOnIdle { enabled.value = !enabled.value }; step(32) }
        step(500); compose.onNodeWithTag("action").assertIsEnabled()
        val active = color("action"); assertTrue(active.blue > .85f); assertEquals(0f, active.red, .01f)
        compose.onNodeWithTag("action").performClick(); assertEquals(1, clicks)
    }

    @Test fun switchSliderAndFieldDisableImmediatelyAndReenableFromTheirCurrentVisualState() {
        val enabled = mutableStateOf(true); var changes = 0
        compose.setContent { MaterialTheme { Column {
            val palette = SwitchDefaults.colors(checkedTrackColor = Color.Blue, disabledCheckedTrackColor = Color.Red,
                checkedBorderColor = Color.Transparent, disabledCheckedBorderColor = Color.Transparent).animateEnabled(enabled.value)
            Switch(true, { changes++ }, Modifier.testTag("switch"), enabled = enabled.value, colors = palette)
            Slider(.5f, { changes++ }, Modifier.testTag("slider"), enabled = enabled.value,
                colors = SliderDefaults.colors(activeTrackColor = Color.Blue, disabledActiveTrackColor = Color.Red).animateEnabled(enabled.value))
            OutlinedTextField("text", { changes++ }, Modifier.testTag("field"), enabled = enabled.value,
                colors = OutlinedTextFieldDefaults.colors().animateEnabled(enabled.value, focused = false))
        } } }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { enabled.value = false }; step(32)
        for (tag in listOf("switch", "slider", "field")) compose.onNodeWithTag(tag).assertIsNotEnabled()
        step(48); mixed(color("switch", .3f)); mixed(color("slider", .25f))
        compose.onNodeWithTag("switch").performTouchInput { click() }; assertEquals(0, changes)
        compose.runOnIdle { enabled.value = true }; step(32)
        mixed(color("switch", .3f)); step(500)
        for (tag in listOf("switch", "slider", "field")) compose.onNodeWithTag(tag).assertIsEnabled()
        assertEquals(1f, color("switch", .3f).blue, .01f)
        compose.onNodeWithTag("field").performTextInput("x"); assertEquals(1, changes)
    }
}
