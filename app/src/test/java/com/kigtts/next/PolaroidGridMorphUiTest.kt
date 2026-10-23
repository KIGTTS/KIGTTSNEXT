package com.kigtts.next

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.theme.*
import com.kigtts.next.ui.cards.*
import com.kigtts.plugin.api.PolaroidCard
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xhdpi")
class PolaroidGridMorphUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun changingColumnsMovesAndScalesTheActualPhotoTogetherIncludingMidFlightReversal() {
        val file = File(compose.activity.cacheDir, "grid-morph-blue.png")
        Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLUE); file.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }; recycle()
        }
        val cards = (0..19).map { PolaroidCard(id = "morph-$it", imageId = if (it == 1) "blue" else null, title = "") }
        var columns by mutableIntStateOf(2)
        var ready by mutableStateOf(false)
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
            val cache = listOf(256, 512, 1024, 1200).map { rememberCardImage(file, it) }
            SideEffect { ready = cache.all { it != null } }
            Box(Modifier.fillMaxSize().testTag("grid-morph-scene")) {
                CardManagementTransition(false, browser = {
                    PolaroidBrowser(cards, PolaroidBrowseMode.GRID, cards[1].id, { file }, emptyList(), true,
                        {}, {}, {}, {}, Modifier.fillMaxSize(), gridColumns = columns)
                }, manager = {})
            }
        } }
        compose.waitUntil(8000) { ready }; compose.waitForIdle(); compose.mainClock.autoAdvance = false
        fun step(frames: Int) { repeat(frames) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() } }
        fun pixels(label: String): Rect {
            val out = File("build/previews/grid-morph-$label.png").apply { parentFile?.mkdirs() }
            compose.onNodeWithTag("grid-morph-scene").captureRoboImage(out.absolutePath)
            val bitmap = requireNotNull(BitmapFactory.decodeFile(out.absolutePath))
            try {
                var l = bitmap.width; var t = bitmap.height; var r = 0; var b = 0; var count = 0
                for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                    val c = bitmap.getPixel(x, y)
                    if (Color.blue(c) > 235 && Color.red(c) < 15 && Color.green(c) < 15) {
                        l = minOf(l, x); t = minOf(t, y); r = maxOf(r, x); b = maxOf(b, y); count++
                    }
                }
                assertTrue("An opaque image must survive column changes: $label", count > 1000)
                assertTrue("Only one moving photo, not two copies: $label", count > (r - l) * (b - t) * .85f)
                return Rect(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat())
            } finally { bitmap.recycle() }
        }
        val start = pixels("two")
        compose.runOnIdle { columns = 3 }; step(2)
        val first = pixels("first")
        assertEquals("First frame must retain the source size", start.width, first.width, 12f)
        step(7); val middle = pixels("middle")
        assertTrue(middle.width < first.width - 8f)
        assertTrue((middle.center - first.center).getDistance() > 8f)
        compose.runOnIdle { columns = 2 }; step(2)
        val reverse = pixels("reverse")
        assertTrue("Reversal starts from the current drawing", kotlin.math.abs(reverse.width - middle.width) < 25f)
        step(30); assertEquals(start.width, pixels("returned").width, 3f)
        compose.runOnIdle { columns = 3 }; step(30)
        val end = pixels("three")
        assertTrue("Middle frame must have an interpolated size", middle.width > end.width + 5f)
    }
}
