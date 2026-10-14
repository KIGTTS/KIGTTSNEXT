package com.kigtts.next

import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.theme.*
import com.kigtts.next.ui.cards.CardManagementPhoto
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
@Config(sdk = [35], qualifiers = "zh-rCN-w600dp-h500dp-xhdpi")
class CardManagementShadowUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun sortingPaperKeepsShadowAndSmoothlyRaisesItWhileHeld() {
        var lifted by mutableStateOf(false)
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
            Box(Modifier.size(500.dp, 400.dp).background(androidx.compose.ui.graphics.Color(0xFFE4EEEE)).testTag("sort-shadow"), contentAlignment = Alignment.Center) {
                CardManagementPhoto(PolaroidCard(id = "shadow", title = ""), false, true, { null }, null, {}, Modifier.width(160.dp), Modifier, lifted)
            }
        } }
        compose.waitForIdle(); compose.mainClock.autoAdvance = false
        val rest = shadow("rest")
        assertTrue("Sorting paper always casts a shadow", rest > 0)
        compose.runOnIdle { lifted = true }; step(80)
        val moving = shadow("raising")
        step(600); val raised = shadow("raised")
        assertTrue("Lift expands the shadow beyond its resting elevation", raised > rest * 2)
        assertTrue("Elevation animates through intermediate values", moving > rest && moving < raised)
        compose.runOnIdle { lifted = false }; step(800)
        assertEquals(rest.toFloat(), shadow("landed").toFloat(), rest * .05f)
    }
    private fun step(ms: Long) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle(); compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }
    private fun shadow(name: String): Int {
        val paper = compose.onNodeWithTag("manage-photo/shadow").fetchSemanticsNode().boundsInRoot
        val root = compose.onNodeWithTag("sort-shadow").fetchSemanticsNode().boundsInRoot
        val file = File("build/previews/management-shadow-$name.png").apply { parentFile?.mkdirs() }
        compose.onNodeWithTag("sort-shadow").captureRoboImage(file.absolutePath)
        val bitmap = requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
        return try {
            var weight = 0
            val baseline = Color.red(bitmap.getPixel(4, 4))
            for (y in (paper.bottom - root.top).toInt() + 2 until (paper.bottom - root.top).toInt() + 32)
                for (x in (paper.left - root.left + paper.width * .3f).toInt() until (paper.right - root.left - paper.width * .3f).toInt())
                    weight += (baseline - Color.red(bitmap.getPixel(x, y))).coerceAtLeast(0)
            weight
        } finally { bitmap.recycle() }
    }
}
