package com.kigtts.next.ui.cards.camera

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.state.CardBurstReview
import com.kigtts.next.theme.*
import com.kigtts.plugin.api.PolaroidFormat
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
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class CameraBurstUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun photos(count: Int): List<PolaroidCameraCapture> = List(count) { index ->
        val file = File.createTempFile("burst-ui-$index", ".jpg")
        val image = Bitmap.createBitmap(144, if (index % 2 == 0) 192 else 96, Bitmap.Config.ARGB_8888)
        image.eraseColor(android.graphics.Color.rgb(35 + index * 7, 110, 140))
        try { file.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, 95, it) } } finally { image.recycle() }
        PolaroidCameraCapture(file, null, PolaroidFormat.MINI, PhotoFilter.ORIGINAL)
    }

    @Test fun heldShutterRemainsThePointerOwnerWhenBusyAndReleaseStopsWithoutAShortCapture() {
        var singles = 0; var starts = 0; var stops = 0
        var running by mutableStateOf(false)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            CameraShutterButton(true, running, false, { singles++ }, Modifier.testTag("shutter"),
                burstStart = { starts++; running = true }, burstEnd = { if (running) { stops++; running = false } },
                burstRunning = running, burstHeld = running, count = 3)
        } }
        compose.onNodeWithTag("shutter").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(700); compose.waitForIdle()
        assertEquals(1, starts)
        compose.onNodeWithTag("shutter").assertIsEnabled().performTouchInput { up() }
        compose.waitForIdle(); assertEquals(0, singles); assertEquals(1, stops)
        compose.onNodeWithTag("shutter").performTouchInput { click() }
        assertEquals(1, singles); assertEquals(1, starts)
    }

    @Test fun gridScrollsAndPreviewSelectionArrowsAndSwipeStayInSyncBeforePrintingOnlySelectedPhotos() {
        val photos = photos(20); var saved: Set<String>? = null
        try {
            compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
                CameraBurstReviewScreen(CardBurstReview("review-ui", photos), false, 0, {}, { saved = it })
            } }
            compose.onNodeWithTag("burst-grid").performScrollToIndex(19)
            compose.onNodeWithTag("burst-photo/19").assertIsDisplayed()
            compose.onNodeWithTag("burst-select-all").performClick()
            compose.onNodeWithTag("burst-print-selected").assertIsNotEnabled()
            compose.onNodeWithTag("burst-grid").performScrollToIndex(0)
            compose.onNodeWithTag("burst-check/0").performClick().assertIsOn()
            compose.onRoot().captureRoboImage("build/previews/burst-grid-light.png")
            compose.onNodeWithTag("burst-photo/0").performClick()
            compose.onNodeWithTag("burst-preview-check").assertIsOn()
            compose.onNodeWithContentDescription("下一张").performClick()
            compose.onNodeWithTag("burst-preview-count").assertTextEquals("2 / 20")
            compose.onNodeWithTag("burst-preview-check").assertIsOff().performClick().assertIsOn()
            compose.onNodeWithTag("burst-preview-pager").performTouchInput {
                swipe(androidx.compose.ui.geometry.Offset(width * .75f, centerY),
                    androidx.compose.ui.geometry.Offset(width * .2f, centerY), 400)
            }
            compose.onNodeWithTag("burst-preview-count").assertTextEquals("3 / 20")
            compose.onNodeWithContentDescription("上一张").performClick()
            compose.onNodeWithTag("burst-preview-check").assertIsOn()
            compose.onRoot().captureRoboImage("build/previews/burst-preview.png")
            compose.onNodeWithContentDescription("关闭预览").performClick()
            compose.onNodeWithTag("burst-check/1").assertIsOn()
            compose.onNodeWithTag("burst-print-selected").performClick()
            assertEquals(setOf(photos[0].original.path, photos[1].original.path), saved)
        } finally { photos.forEach { it.discard() } }
    }

    @Test fun saveAllBypassesAnEmptySelectionAndDarkGridKeepsItsControlsReadable() {
        val photos = photos(5); var saved: Set<String>? = null
        try {
            compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.DARK)) {
                CameraBurstReviewScreen(CardBurstReview("review-all", photos), false, 0, {}, { saved = it })
            } }
            compose.onNodeWithTag("burst-select-all").performClick()
            compose.onRoot().captureRoboImage("build/previews/burst-grid-dark.png")
            compose.onNodeWithTag("burst-save-all").performClick()
            assertEquals(photos.map { it.original.path }.toSet(), saved)
        } finally { photos.forEach { it.discard() } }
    }

    @Test fun heldRangeCanDeselectReselectAndScrollToPhotosBeyondTheViewport() {
        val photos = photos(60)
        try {
            compose.setContent { KigttsTheme(ThemeSettings()) {
                CameraBurstReviewScreen(CardBurstReview("range-ui", photos), false, 0, {}, {})
            } }
            fun center(index: Int): androidx.compose.ui.geometry.Offset {
                val grid = compose.onNodeWithTag("burst-grid").fetchSemanticsNode().boundsInRoot
                return compose.onNodeWithTag("burst-photo/$index").fetchSemanticsNode().boundsInRoot.center - grid.topLeft
            }
            val first = center(0); val fourth = center(4)
            compose.onNodeWithTag("burst-grid").performTouchInput { down(first) }
            compose.mainClock.advanceTimeBy(700); compose.waitForIdle()
            compose.onNodeWithTag("burst-grid").performTouchInput { moveTo(fourth); up() }
            compose.onNodeWithTag("burst-check/0").assertIsOff()
            compose.onNodeWithTag("burst-check/4").assertIsOff()
            compose.onNodeWithTag("burst-check/5").assertIsOn()
            compose.onNodeWithTag("burst-grid").performTouchInput { down(first) }
            compose.mainClock.advanceTimeBy(700); compose.waitForIdle()
            compose.onNodeWithTag("burst-grid").performTouchInput { moveTo(fourth); up() }
            compose.onNodeWithTag("burst-check/0").assertIsOn()
            compose.onNodeWithTag("burst-check/4").assertIsOn()
            compose.onNodeWithTag("burst-grid").performTouchInput { down(first) }
            compose.mainClock.advanceTimeBy(700); compose.waitForIdle()
            compose.onNodeWithTag("burst-check/0").assertIsOff()
            compose.mainClock.autoAdvance = false
            compose.onNodeWithTag("burst-grid").performTouchInput { moveTo(androidx.compose.ui.geometry.Offset(first.x, height - 2f)) }
            compose.waitForIdle()
            // A held edge must keep moving even without further pointer events.
            compose.mainClock.advanceTimeBy(2200); compose.waitForIdle()
            compose.onNodeWithTag("burst-grid").performTouchInput { up() }
            compose.mainClock.autoAdvance = true
            compose.onNodeWithTag("burst-photo/0").assertDoesNotExist()
            compose.onNodeWithTag("burst-preview").assertDoesNotExist()
            compose.onNodeWithTag("burst-grid").performScrollToIndex(20)
            compose.onNodeWithTag("burst-check/20").assertIsOff()
        } finally { photos.forEach { it.discard() } }
    }

    @Test fun heldTopEdgeScrollsBackwardWithoutOpeningThePhotoPreview() {
        val photos = photos(60)
        try {
            compose.setContent { KigttsTheme(ThemeSettings()) {
                CameraBurstReviewScreen(CardBurstReview("range-top", photos), false, 0, {}, {})
            } }
            compose.onNodeWithTag("burst-grid").performScrollToIndex(30)
            val grid = compose.onNodeWithTag("burst-grid").fetchSemanticsNode().boundsInRoot
            val start = compose.onNodeWithTag("burst-photo/30").fetchSemanticsNode().boundsInRoot.center - grid.topLeft
            compose.onNodeWithTag("burst-grid").performTouchInput { down(start) }
            compose.mainClock.advanceTimeBy(700); compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            compose.onNodeWithTag("burst-grid").performTouchInput { moveTo(androidx.compose.ui.geometry.Offset(start.x, 2f)) }
            compose.waitForIdle(); compose.mainClock.advanceTimeBy(2200); compose.waitForIdle()
            compose.onNodeWithTag("burst-grid").performTouchInput { up() }
            compose.mainClock.autoAdvance = true
            compose.onNodeWithTag("burst-preview").assertDoesNotExist()
            compose.onNodeWithTag("burst-grid").performScrollToIndex(24)
            compose.onNodeWithTag("burst-check/24").assertIsOff()
        } finally { photos.forEach { it.discard() } }
    }

    @Test fun reversedRangeRestoresTheSelectionOutsideTheCurrentRange() {
        val add = BurstSelectionRange(2, setOf(0, 8))
        assertEquals(listOf(0, 2, 3, 4, 5, 8), add.at(5))
        assertEquals(listOf(0, 2, 3, 8), add.at(3))
        val remove = BurstSelectionRange(2, (0..8).toSet())
        assertEquals(listOf(0, 1, 6, 7, 8), remove.at(5))
        assertEquals(listOf(0, 1, 4, 5, 6, 7, 8), remove.at(3))
    }
}
