package com.kigtts.next

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
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
@Config(sdk = [35], qualifiers = "zh-rCN-w1000dp-h700dp-xhdpi")
class PolaroidCarouselUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun bothOuterPhotographsRemainVisibleAcrossMixedWidthsAndViewportResizing() {
        val colors = listOf(Color.RED, Color.BLUE, Color.GREEN, Color.BLUE, Color.RED)
        val images = colors.mapIndexed { index, color ->
            File(compose.activity.cacheDir, "five-photo-$index.png").also { file ->
                val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            }
        }
        var width by mutableStateOf(900.dp)
        var cards by mutableStateOf(listOf("wide", "mini", "square", "wide", "mini").mapIndexed { index, format ->
            PolaroidCard(id = "photo-$index", title = "", imageId = index.toString(), photoZoom = 3.0, frameStyleId = "instax.$format.classic")
        })
        var focused by mutableStateOf(cards[2].id)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Surface(Modifier.size(width, 360.dp).testTag("five-photo-carousel")) {
                PolaroidBrowser(cards, PolaroidBrowseMode.STACK, focused, { images[it.toInt()] }, emptyList(), true,
                    {}, { focused = it.id }, {}, {}, Modifier.fillMaxSize(), wideLayout = true)
            }
        } }
        fun point(index: Int, x: Float, y: Float): Offset {
            val coords = compose.onNodeWithTag("polaroid/photo-$index").fetchSemanticsNode().layoutInfo.coordinates
            return coords.localToRoot(Offset(coords.size.width * x, coords.size.height * y))
        }
        fun verify(name: String) {
            compose.waitForIdle()
            val bounds = compose.onNodeWithTag("five-photo-carousel").fetchSemanticsNode().boundsInRoot
            for (side in listOf(-1, 1)) {
                val far = 2 + side * 2; val near = 2 + side
                val outer = if (side < 0) 0f else 1f
                val outerPoint = point(far, outer, .5f)
                assertTrue("Second photo stays within the viewport", bounds.contains(outerPoint))
                assertTrue("Second photo peeks beyond the first", (outerPoint.x - point(near, outer, .5f).x) * side > 8f)
                assertTrue("The primary photo stays clear", if (side < 0) point(near, 1f, .5f).x < point(2, 0f, .5f).x
                    else point(near, 0f, .5f).x > point(2, 1f, .5f).x)
            }
            compose.waitUntil(8_000) {
                val output = File("build/previews/five-photo-$name.png").apply { parentFile.mkdirs() }
                compose.onNodeWithTag("five-photo-carousel").captureRoboImage(output.absolutePath)
                val bitmap = requireNotNull(BitmapFactory.decodeFile(output.absolutePath))
                try { listOf(0, 4).all { index ->
                    // Sample the exposed outer photo, not its overlap with its neighbor.
                    val sample = point(index, if (index == 0) .10f else .90f, .4f) - bounds.topLeft
                    val pixel = bitmap.getPixel((sample.x / bounds.width * bitmap.width).toInt(),
                        (sample.y / bounds.height * bitmap.height).toInt())
                    Color.red(pixel) - Color.green(pixel) > 25
                } } finally { bitmap.recycle() }
            }
            compose.onNodeWithTag("five-photo-carousel").captureRoboImage("build/previews/five-photo-$name.png")
        }
        verify("wide")
        compose.runOnIdle { width = 540.dp }
        verify("narrow")
        compose.runOnIdle { cards = cards.mapIndexed { index, card -> card.copy(frameStyleId =
            "instax." + listOf("mini", "wide", "wide", "mini", "square")[index] + ".classic") } }
        verify("wide-center")
        compose.onNodeWithTag("cards-stack").performTouchInput { swipeLeft() }
        compose.waitForIdle(); assertEquals("photo-3", focused)
        compose.onNodeWithTag("cards-stack").performTouchInput { swipeRight() }
        compose.waitForIdle(); assertEquals("photo-2", focused)
        verify("returned")
    }
}
