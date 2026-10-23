package com.kigtts.next

import android.app.Application
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.*
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.ui.home.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "w400dp-h600dp-mdpi")
class SubtitleCursorGeometryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val measurer = TextMeasurer(createFontFamilyResolver(ApplicationProvider.getApplicationContext<Application>()), Density(1f), LayoutDirection.Ltr)
    private fun frame(font: Int, width: Int, y: Int = 20): SubtitleGlyphFrame {
        val layout = measurer.measure("ABCDE", TextStyle(fontSize = font.sp), constraints = Constraints.fixedWidth(width))
        return subtitleGlyphFrame(layout, IntOffset(0, y), IntSize(width, 300))
    }
    private fun cursorPixels(name: String): android.graphics.Rect {
        val path = "build/previews/caret-$name.png"
        compose.onNodeWithTag("caret").captureRoboImage(path)
        val bitmap = BitmapFactory.decodeFile(path)
        val result = android.graphics.Rect(bitmap.width, bitmap.height, -1, -1)
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            if (android.graphics.Color.red(pixel) > android.graphics.Color.green(pixel) + 15) {
                result.left = minOf(result.left, x); result.right = maxOf(result.right, x + 1)
                result.top = minOf(result.top, y); result.bottom = maxOf(result.bottom, y + 1)
            }
        }
        bitmap.recycle()
        assertTrue("The breathing cursor should remain visible", result.right > result.left)
        return result
    }
    @Test fun geometryChangesSnapToTheMeasuredCaretWhileTypingStillMovesSmoothly() {
        var current by mutableStateOf(frame(30, 300))
        var offset by mutableIntStateOf(1)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                SubtitleCursorMotion(current, offset, Color.Red,
                    Modifier.size(360.dp, 350.dp).background(Color.White).testTag("caret"))
            }
        }
        compose.mainClock.autoAdvance = false
        val initial = cursorPixels("initial").left
        compose.runOnIdle { offset = 4 }
        repeat(5) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        val typing = cursorPixels("typing").left
        val typingEnd = current.layout.getCursorRect(offset).left
        assertTrue("Normal typing retains a short movement: $initial -> $typing -> $typingEnd", typing > initial && typing < typingEnd)
        for ((index, next) in listOf(frame(48, 300), frame(48, 120, 90), frame(25, 250, 60)).withIndex()) {
            compose.runOnIdle { current = next }
            // One frame commits composition, the next renders it in Robolectric.
            repeat(2) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
            val pixels = cursorPixels("geometry-$index")
            val expected = next.layout.getCursorRect(offset).translate(next.origin)
            assertEquals("No trailing position after resizing", expected.left, pixels.left.toFloat(), 1.1f)
            assertEquals("No trailing row after reflow", expected.top, pixels.top.toFloat(), 1.1f)
            assertEquals("Caret height follows the font immediately", expected.height, pixels.height().toFloat(), 1.1f)
        }
    }
}
