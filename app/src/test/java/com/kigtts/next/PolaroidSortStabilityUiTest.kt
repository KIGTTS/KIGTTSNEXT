package com.kigtts.next

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.*
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
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
@Config(sdk = [35], qualifiers = "zh-rCN-w700dp-h500dp-xhdpi")
class PolaroidSortStabilityUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun holdingASortedPhotoStillDoesNotOscillateVibrateOrLoseItsImage() {
        val file = File(compose.activity.cacheDir, "sort-stable-blue.png")
        val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val cards = listOf("a", "b", "c").associateWith { PolaroidCard(id = it, title = "", imageId = if (it == "a") "blue" else null) }
        var keys by mutableStateOf(cards.keys.toList())
        val signals = mutableListOf<HapticFeedbackType>(); var commits = 0; var loaded by mutableStateOf(false)
        val feedback = object : HapticFeedback { override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { signals += hapticFeedbackType } }
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
            val photo = rememberCardImage(file, 512); SideEffect { loaded = photo != null }
            CompositionLocalProvider(LocalHapticFeedback provides feedback, LocalCardManagementReveal provides remember { mutableStateOf(1f) }) {
                Box(Modifier.size(600.dp, 360.dp).testTag("sort-stable-scene")) {
                    CardManagementSortPane(keys, CardManagementMode.STRIP, 2, true, false, "a",
                        order = { keys = it; commits++ }) { id, modifier, grip, lifted ->
                        CardManagementPhoto(cards.getValue(id), id == "a", true, { file }, null, {}, modifier, grip, lifted, "a")
                    }
                }
            }
        } }
        compose.waitUntil(8000) { loaded }; compose.waitForIdle(); compose.mainClock.autoAdvance = false
        val pane = compose.onNodeWithTag("card-management-sort").fetchSemanticsNode().boundsInRoot
        val grip = compose.onNodeWithTag("manage-photo-grip/a", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithTag("manage-photo/b").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("card-management-sort").performTouchInput { down(grip.center - pane.topLeft); moveTo(target.center - pane.topLeft, 200) }
        fun photoCount(label: String): Int {
            val output = File("build/previews/sort-stable-$label.png").apply { parentFile?.mkdirs() }
            compose.onNodeWithTag("sort-stable-scene").captureRoboImage(output.absolutePath)
            val pixels = requireNotNull(BitmapFactory.decodeFile(output.absolutePath))
            return try {
                var count = 0
                for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                    val p = pixels.getPixel(x, y)
                    if (Color.blue(p) > 230 && Color.red(p) < 20 && Color.green(p) < 20) count++
                }
                count
            } finally { pixels.recycle() }
        }
        compose.mainClock.advanceTimeBy(32); compose.waitForIdle()
        val initial = photoCount("picked"); assertTrue(initial > 1000)
        val ticks = signals.count { it == HapticFeedbackType.SegmentFrequentTick }; assertEquals(1, ticks)
        repeat(8) { step ->
            compose.onNodeWithTag("card-management-sort").performTouchInput { moveBy(Offset.Zero, 100) }
            compose.mainClock.advanceTimeBy(64); compose.waitForIdle()
            assertEquals("Neighbor animation must not cause repeated swaps", ticks, signals.count { it == HapticFeedbackType.SegmentFrequentTick })
            assertEquals("Floating image must not disappear or duplicate", initial.toFloat(), photoCount("hold-$step").toFloat(), initial * .03f)
        }
        compose.onNodeWithTag("card-management-sort").performTouchInput { up() }
        compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1000); compose.waitForIdle()
        assertEquals(1, commits); assertEquals(listOf("b", "a", "c"), keys)
        assertEquals(initial.toFloat(), photoCount("settled").toFloat(), initial * .03f)
    }
}
