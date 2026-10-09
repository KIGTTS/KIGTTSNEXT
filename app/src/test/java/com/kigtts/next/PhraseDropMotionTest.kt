package com.kigtts.next

import android.app.Application
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.state.*
import com.kigtts.next.ui.KigttsApp
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
class PhraseDropMotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val density get() = compose.activity.resources.displayMetrics.density
    private fun step(time: Long) { compose.mainClock.advanceTimeBy(time); compose.waitForIdle() }
    private fun capture(name: String) = compose.onRoot().captureRoboImage("build/previews/" + name + ".png")
    private fun point(child: SemanticsNodeInteraction, pane: SemanticsNodeInteraction): Offset {
        val c = child.getUnclippedBoundsInRoot(); val p = pane.getUnclippedBoundsInRoot()
        return Offset(((c.left.value + c.right.value) / 2 - p.left.value) * density,
            ((c.top.value + c.bottom.value) / 2 - p.top.value) * density)
    }
    @Test fun pickupIsOpaqueAndReleaseAnimatesBeforeHandingBackToTheFinalSlot() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("appearance", "ui-debug", "home-panel", "voice-input", "interaction", "quick-phrases").forEach {
            app.getSharedPreferences(it, 0).edit().clear().commit()
        }
        val model = testAppModel(app)
        compose.setContent { KigttsApp(model) }
        compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT); model.editQuickTexts() }
        val phrase = model.phrases.value.first()
        val other = model.phrases.value.filter { it.category == phrase.category }[1]
        val pane = compose.onNodeWithTag("quick-editor-pane")
        val original = compose.onNodeWithTag("edit-phrase-" + phrase.id)
        fun background(node: SemanticsNodeInteraction, name: String): Int {
            val bounds = node.getUnclippedBoundsInRoot()
            val path = "build/previews/" + name + ".png"
            compose.onRoot().captureRoboImage(path)
            val bitmap = BitmapFactory.decodeFile(path)
            return bitmap.getPixel(((bounds.left.value + (bounds.right - bounds.left).value * .65f) * density).toInt(),
                ((bounds.bottom.value - 8f) * density).toInt())
        }
        val color = background(original, "phrase-pickup-before")
        val from = point(compose.onNodeWithTag("phrase-grip-" + phrase.id, useUnmergedTree = true), pane)
        val to = point(compose.onNodeWithTag("edit-phrase-" + other.id), pane)
        val target = compose.onNodeWithTag("edit-phrase-" + other.id).getUnclippedBoundsInRoot()
        compose.mainClock.autoAdvance = false
        pane.performTouchInput { down(from); moveBy(Offset(0f, 26f * density)) }
        step(16)
        val floating = compose.onNodeWithTag("dragged-phrase")
        assertEquals("The picked card is opaque on its first frame", color, background(floating, "phrase-pickup-first-frame"))
        pane.performTouchInput { moveTo(to + Offset(10f * density, 10f * density), delayMillis = 100) }
        step(80)
        val before = floating.getUnclippedBoundsInRoot()
        pane.performTouchInput { up() }
        compose.waitForIdle()
        val seeded = floating.getUnclippedBoundsInRoot()
        capture("phrase-drop-release-seeded")
        assertTrue("left before=" + before + ", seeded=" + seeded, abs(seeded.left.value - before.left.value) < 2f)
        assertTrue("top before=" + before + ", seeded=" + seeded, abs(seeded.top.value - before.top.value) < 2f)
        step(16)
        fun distance(bounds: androidx.compose.ui.unit.DpRect) = abs(bounds.left.value - target.left.value) + abs(bounds.top.value - target.top.value)
        assertTrue(distance(seeded) > 15f)
        step(96)
        val mid = floating.getUnclippedBoundsInRoot()
        assertTrue("Release moves through intermediate positions", distance(mid) < distance(seeded) - 5f)
        assertTrue(distance(mid) > 1f)
        capture("phrase-drop-return-mid")
        var lastFloating = mid
        repeat(90) {
            if (compose.onAllNodesWithTag("dragged-phrase").fetchSemanticsNodes().isNotEmpty()) {
                lastFloating = floating.getUnclippedBoundsInRoot(); step(16)
            }
        }
        compose.mainClock.autoAdvance = true
        floating.assertDoesNotExist()
        original.assertIsDisplayed()
        val handedBack = original.getUnclippedBoundsInRoot()
        assertEquals("Drop and final list card share the same padded coordinate", handedBack.top.value, lastFloating.top.value, 1.5f)
        assertEquals(handedBack.left.value, lastFloating.left.value, 1.5f)
        assertEquals(phrase.id, model.phrases.value.filter { it.category == phrase.category }[1].id)
        capture("phrase-drop-handoff-complete")
        original.performClick()
        compose.onNodeWithTag("phrase-detail-text").assertIsDisplayed()
    }
}
