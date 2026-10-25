package com.kigtts.next.ui.cards.camera

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
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
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class BatchPolaroidPrinterUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun frames(count: Int) = repeat(count) {
        compose.mainClock.advanceTimeByFrame(); compose.waitForIdle(); ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS)
    }
    @Test fun everySheetRemainsStraightUntilOutsideThePrinter() {
        for (index in 0..10) for (progress in listOf(0f, .5f, .999f))
            assertEquals(0f, batchPrintRotation(7, index, progress, 1f), 0f)
        assertEquals(0f, batchPrintRotation(7, 1, 1f, 0f), 0f)
        assertTrue(kotlin.math.abs(batchPrintRotation(7, 1, 1f, 1f)) <= 3f)
        assertNotEquals(batchPrintRotation(7, 1, 1f, 1f), batchPrintRotation(7, 2, 1f, 1f))
    }
    @Test fun fastBatchRetainsItsStackAndOnlyCompletesOnceWhenItsPageIsActive() {
        val cards = List(3) { PolaroidCard(title = "连拍 ${it + 1}", frameStyleId = "instax.mini.classic") }
        var active by mutableStateOf(true); var completions = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { KigttsTheme(ThemeSettings()) { Box(Modifier.size(360.dp, 640.dp)) {
            BatchPolaroidPrinter(CardPrintRequest("batch-ui", cards.last(), cards), { null },
                { PolaroidFrameStyle(it.frameStyleId, "Mini", format = PolaroidFormat.MINI) }, active, { completions++; true })
        } } }
        frames(50)
        compose.onNodeWithTag("batch-paper/0").assertExists()
        compose.onRoot().captureRoboImage("build/previews/batch-printer-mid.png")
        compose.runOnIdle { active = false }; frames(200)
        assertEquals(0, completions)
        compose.runOnIdle { active = true }; frames(160)
        assertEquals(1, completions)
        for (index in cards.indices) compose.onNodeWithTag("batch-paper/$index").assertExists()
        compose.onRoot().captureRoboImage("build/previews/batch-printer-stack.png")
        frames(15); assertEquals(1, completions)
        compose.mainClock.autoAdvance = true
    }
}
