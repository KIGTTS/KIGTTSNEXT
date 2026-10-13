package com.kigtts.next.ui.cards.camera

import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.state.CardPrintRequest
import com.kigtts.next.theme.*
import com.kigtts.plugin.api.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class PolaroidPrinterUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val card = PolaroidCard(title = "打印照片", frameStyleId = "instax.mini.classic")
    private val frame = PolaroidFrameStyle(card.frameStyleId, "Mini", format = PolaroidFormat.MINI)
    private fun frames(count: Int) = repeat(count) {
        compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
        ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS)
    }
    @Test fun printingKeepsThePaperAspectAndPublishesItsCompletionOnlyOnce() {
        var delivered = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
            Box(Modifier.size(360.dp, 640.dp)) {
                PolaroidPrinter(CardPrintRequest("print-test", card), { null }, frame, true, { delivered++; true })
            }
        } }
        frames(30)
        val paper = compose.onNodeWithTag("printer-paper").getUnclippedBoundsInRoot()
        assertEquals(PolaroidFormat.MINI.geometry.aspectRatio,
            ((paper.right - paper.left) / (paper.bottom - paper.top)).toDouble(), .01)
        assertEquals(0, delivered)
        frames(50)
        val next = compose.onNodeWithTag("printer-paper").getUnclippedBoundsInRoot()
        assertEquals((paper.right - paper.left).value, (next.right - next.left).value, .5f)
        assertEquals((paper.bottom - paper.top).value, (next.bottom - next.top).value, .5f)
        assertTrue("Printing moves the complete paper below its slot", next.top > paper.top)
        compose.onRoot().captureRoboImage("build/previews/polaroid-printer-mid.png")
        frames(60)
        assertEquals(1, delivered)
        frames(20); assertEquals(1, delivered)
        compose.mainClock.autoAdvance = true
    }

    @Test fun thePrinterHousingChangesWithTheNativeLightAndDarkTheme() {
        var mode by mutableStateOf(ThemeMode.LIGHT)
        compose.mainClock.autoAdvance = false
        compose.setContent { KigttsTheme(ThemeSettings(mode = mode)) {
            Box(Modifier.size(360.dp, 640.dp)) {
                PolaroidPrinter(CardPrintRequest("print-theme", card), { null }, frame, true, { true })
            }
        } }
        frames(30)
        fun housing(name: String): Int {
            val file = File("build/previews/polaroid-printer-" + name + ".png")
            compose.onNodeWithTag("printer-housing").captureRoboImage(file.absolutePath)
            val bitmap = file.inputStream().use { requireNotNull(BitmapFactory.decodeStream(it)) }
            return try { bitmap.getPixel(bitmap.width / 2, 4) } finally { bitmap.recycle() }
        }
        val light = housing("light")
        compose.runOnIdle { mode = ThemeMode.DARK }; frames(32)
        val dark = housing("dark")
        assertTrue(android.graphics.Color.red(light) > android.graphics.Color.red(dark) + 70)
        assertTrue(android.graphics.Color.green(light) > android.graphics.Color.green(dark) + 70)
        assertTrue(android.graphics.Color.blue(light) > android.graphics.Color.blue(dark) + 70)
        compose.mainClock.autoAdvance = true
    }

    @Test fun aCoveredPrinterWaitsForItsPageAndThenDeliversExactlyOnce() {
        var active by mutableStateOf(false); var delivered = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Box(Modifier.size(360.dp, 640.dp)) {
                PolaroidPrinter(CardPrintRequest("covered-print", card), { null }, frame, active, { delivered++; true })
            }
        } }
        frames(150); assertEquals(0, delivered)
        compose.runOnIdle { active = true }; frames(2)
        assertEquals(1, delivered)
        compose.runOnIdle { active = false }; frames(2)
        compose.runOnIdle { active = true }; frames(2)
        assertEquals(1, delivered)
        compose.mainClock.autoAdvance = true
    }
}
