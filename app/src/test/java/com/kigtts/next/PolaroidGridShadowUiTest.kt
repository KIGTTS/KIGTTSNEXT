package com.kigtts.next

import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.theme.KigttsTheme
import com.kigtts.next.theme.ThemeMode
import com.kigtts.next.theme.ThemeSettings
import com.kigtts.next.ui.cards.PolaroidBrowseMode
import com.kigtts.next.ui.cards.PolaroidBrowser
import com.kigtts.plugin.api.PolaroidCard
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class PolaroidGridShadowUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun galleryPaperCastsItsShadowBeyondTheGridItemOnTheBottomAndSide() {
        val card = PolaroidCard(title = "阴影边界")
        compose.setContent {
            KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
                Box(Modifier.fillMaxSize().background(Color(0xFFE4EEEE)).testTag("grid-shadow-viewport")) {
                    PolaroidBrowser(listOf(card), PolaroidBrowseMode.GRID, card.id, { null }, emptyList(),
                        true, {}, {}, {}, {}, Modifier.fillMaxSize())
                }
            }
        }
        compose.waitForIdle()
        val root = compose.onNodeWithTag("grid-shadow-viewport").fetchSemanticsNode().boundsInRoot
        val paper = compose.onNodeWithTag("polaroid/" + card.id).fetchSemanticsNode().boundsInRoot
        val output = File("build/previews/polaroid-grid-shadow.png")
        output.parentFile.mkdirs()
        compose.onNodeWithTag("grid-shadow-viewport").captureRoboImage(output.absolutePath)
        val bitmap = output.inputStream().use { requireNotNull(BitmapFactory.decodeStream(it)) }
        try {
            fun x(value: Float) = ((value - root.left) / root.width * bitmap.width).toInt()
            fun y(value: Float) = ((value - root.top) / root.height * bitmap.height).toInt()
            val baseline = bitmap.getPixel(bitmap.width - 8, y(paper.center.y))
            fun shadowCount(left: Int, top: Int, right: Int, bottom: Int): Int {
                var count = 0
                for (row in top until bottom) for (column in left until right) {
                    val pixel = bitmap.getPixel(column, row)
                    if (android.graphics.Color.red(pixel) < android.graphics.Color.red(baseline) - 1 &&
                        android.graphics.Color.green(pixel) < android.graphics.Color.green(baseline) - 1 &&
                        android.graphics.Color.blue(pixel) < android.graphics.Color.blue(baseline) - 1) count++
                }
                return count
            }
            assertTrue("Paper shadow must remain visible below its layout bounds",
                shadowCount(x(paper.left + paper.width * .25f), y(paper.bottom) + 1,
                    x(paper.right - paper.width * .25f), y(paper.bottom) + 18) > 4)
            assertTrue("Paper shadow must remain visible beside its layout bounds",
                shadowCount(x(paper.right) + 1, y(paper.top + paper.height * .3f),
                    x(paper.right) + 18, y(paper.bottom - paper.height * .3f)) > 4)
        } finally { bitmap.recycle() }
    }
}
