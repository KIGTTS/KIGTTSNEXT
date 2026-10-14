package com.kigtts.next

import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
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
class PolaroidCarouselShadowUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun mixedWidthPaperKeepsItsShadowWhileFadingInBothSwipeDirections() {
        val cards = listOf("wide", "mini", "square", "wide", "mini").mapIndexed { i, format ->
            PolaroidCard(id = "shadow-$i", title = "", frameStyleId = "instax.$format.classic")
        }
        var focused by mutableStateOf(cards[2].id)
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
            Box(Modifier.size(900.dp, 360.dp).background(MaterialTheme.colorScheme.background).testTag("shadow-carousel")) {
                PolaroidBrowser(cards, PolaroidBrowseMode.STACK, focused, { null }, emptyList(), true,
                    {}, { focused = it.id }, {}, {}, Modifier.fillMaxSize(), wideLayout = true)
            }
        } }
        compose.waitForIdle(); compose.mainClock.autoAdvance = false
        fun verify(name: String, id: String, from: Float = .25f, to: Float = .75f) {
            compose.waitForIdle()
            val root = compose.onNodeWithTag("shadow-carousel").fetchSemanticsNode().boundsInRoot
            val coords = compose.onNodeWithTag("polaroid/$id").fetchSemanticsNode().layoutInfo.coordinates
            val left = coords.localToRoot(Offset(0f, coords.size.height.toFloat())) - root.topLeft
            val right = coords.localToRoot(Offset(coords.size.width.toFloat(), coords.size.height.toFloat())) - root.topLeft
            val file = File("build/previews/carousel-shadow-$name.png").apply { parentFile?.mkdirs() }
            compose.onNodeWithTag("shadow-carousel").captureRoboImage(file.absolutePath)
            val bitmap = requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
            try {
                val xScale = bitmap.width / root.width; val yScale = bitmap.height / root.height
                val backdrop = bitmap.getPixel(bitmap.width / 2, 4)
                var count = 0
                for (x in ((left.x + (right.x - left.x) * from) * xScale).toInt() until ((left.x + (right.x - left.x) * to) * xScale).toInt()) {
                    for (y in (left.y * yScale).toInt() + 2 until (left.y * yScale).toInt() + 12) {
                        val pixel = bitmap.getPixel(x, y)
                        if (Color.red(pixel) < Color.red(backdrop) - 1 && Color.green(pixel) < Color.green(backdrop) - 1) count++
                    }
                }
                assertTrue("Faded paper shadow survives outside the paper ($name: $count pixels)", count > 4)
            } finally { bitmap.recycle() }
        }
        verify("settled", "shadow-2")
        verify("outer-left", "shadow-0", .05f, .12f)
        verify("outer-right", "shadow-4", .88f, .95f)
        compose.onNodeWithTag("cards-stack").performTouchInput { down(center); moveBy(Offset(-width * .22f, 0f), 240) }
        compose.mainClock.advanceTimeBy(32); verify("forward", "shadow-2"); verify("incoming", "shadow-3")
        compose.onNodeWithTag("cards-stack").performTouchInput { moveBy(Offset(width * .1f, 0f), 160) }
        compose.mainClock.advanceTimeBy(32); verify("reverse", "shadow-2")
        compose.onNodeWithTag("cards-stack").performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(1000); compose.waitForIdle()
    }
}
