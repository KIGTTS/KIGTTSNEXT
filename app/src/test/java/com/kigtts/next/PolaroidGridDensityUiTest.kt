package com.kigtts.next

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
@Config(sdk = [35], qualifiers = "zh-rCN-w1000dp-h600dp-land-xhdpi")
class PolaroidGridDensityUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun denserGridRevealsNewPhotosInOrderBelowTheHeaderAndCanReverse() {
        val file = File(compose.activity.cacheDir, "density-blue.png")
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLUE); file.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }; recycle()
        }
        val cards = (0..23).map { PolaroidCard(id = "density-$it", imageId = if (it >= 8) "blue" else null, title = "") }
        var columns by mutableIntStateOf(4); var ready by mutableStateOf(false)
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
            val loaded = rememberCardImage(file, 1200); SideEffect { ready = loaded != null }
            CompositionLocalProvider(LocalCardHeaderClearance provides 64.dp, LocalCardWindowLandscape provides true) {
                Box(Modifier.size(800.dp, 400.dp).background(androidx.compose.ui.graphics.Color(0xFFE4EEEE)).testTag("density-scene")) {
                    PolaroidGallery(cards, { file }, emptyList(), true, {}, {}, columns, true, cards[0].id)
                }
            }
        } }
        compose.waitUntil(8000) { ready }; compose.waitForIdle()
        val scene = compose.onNodeWithTag("density-scene").fetchSemanticsNode().boundsInRoot
        val grid = compose.onNodeWithTag("cards-grid").fetchSemanticsNode().boundsInRoot
        assertEquals(scene.top + 128f, grid.top, 1f)
        val first = compose.onNodeWithTag("polaroid/density-0").fetchSemanticsNode().boundsInRoot
        val fourth = compose.onNodeWithTag("polaroid/density-3").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, fourth.top, 1f)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { columns = 6 }
        step(96)
        assertTrue("Extra papers wait until existing photos make room", blueAmount("making-room", 8) < 20)
        step(224)
        val earlyFirst = blueAmount("early", 8)
        val earlyNext = blueAmount("early-next", 9)
        assertTrue("New photos must fade in instead of appearing fully opaque", earlyFirst in 1..240)
        assertTrue("Earlier newly revealed photo leads the next", earlyFirst > earlyNext)
        step(500)
        assertTrue(blueAmount("settled", 8) > 245)
        compose.runOnIdle { columns = 4 }; step(32)
        compose.runOnIdle { columns = 6 }; step(600)
        assertTrue("Rapid density reversal cannot leave a photo invisible", blueAmount("reversed", 8) > 245)
        compose.onNodeWithTag("cards-grid").performTouchInput { swipeUp() }
        step(600)
        assertEquals("Scrolling viewport remains below the transparent title", grid.top,
            compose.onNodeWithTag("cards-grid").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    private fun step(ms: Long) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle(); compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }
    private fun blueAmount(name: String, index: Int): Int {
        val root = compose.onNodeWithTag("density-scene").fetchSemanticsNode().boundsInRoot
        val photo = compose.onNodeWithTag("polaroid/density-$index").fetchSemanticsNode().layoutInfo.coordinates
        val point = photo.localToRoot(Offset(photo.size.width * .5f, photo.size.height * .35f)) - root.topLeft
        val file = File("build/previews/grid-density-$name.png").apply { parentFile?.mkdirs() }
        compose.onNodeWithTag("density-scene").captureRoboImage(file.absolutePath)
        val bitmap = requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
        return try {
            val p = bitmap.getPixel((point.x * bitmap.width / root.width).toInt(), (point.y * bitmap.height / root.height).toInt())
            Color.blue(p) - Color.red(p)
        } finally { bitmap.recycle() }
    }
}
