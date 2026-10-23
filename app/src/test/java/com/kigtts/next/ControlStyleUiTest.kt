@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next

import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.theme.*
import com.kigtts.next.ui.components.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class ControlStyleUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun tabsUseTheActiveThemeInLightAndDarkEvenWithAnImageControlPalette() {
        val dark = mutableStateOf(false)
        val light = lightColorScheme(primaryContainer = Color(0xFFFFDAD6))
        val night = darkColorScheme(primaryContainer = Color(0xFF73342C))
        compose.setContent { MaterialTheme(colorScheme = if (dark.value) night else light) {
            CompositionLocalProvider(LocalUseCompactControlPalette provides true,
                LocalCompactControlPalette provides CompactControlPalette(Color.Green, Color.Black, Color.Blue)) {
                Column(Modifier.width(360.dp).padding(8.dp)) {
                    ElasticTab(true, {}, remember { MutableInteractionSource() }, CornerNeighbors(), false,
                        Modifier.testTag("tab"), icon = { Icon(Icons.Default.Image, null) }, label = { Text("相册") })
                    ChoiceButtonGroup("风格", listOf("自动", "手动"), "自动", { it }, {})
                }
            }
        } }
        listOf(false, true).forEach { mode ->
            compose.runOnIdle { dark.value = mode }; compose.mainClock.advanceTimeBy(600); compose.waitForIdle()
            val file = "build/previews/theme-tab-${if (mode) "dark" else "light"}.png"
            compose.onNodeWithTag("tab").captureRoboImage(file)
            val bitmap = BitmapFactory.decodeFile(file)
            try { assertEquals((if (mode) night else light).primaryContainer.toArgb(), bitmap.getPixel(bitmap.width / 2, bitmap.height - 12)) }
            finally { bitmap.recycle() }
        }
    }
    @Test fun partialPanelDragBlendsThePolaroidIconWithoutReplacingItsTouchTarget() {
        val fraction = mutableFloatStateOf(0f); var clicks = 0
        compose.setContent { MaterialTheme {
            ToolButton("拍立得", Icons.Default.PhotoLibrary, { clicks++ }, Modifier.testTag("photo"),
                size = 48.dp, iconBlend = ToolIconBlend(Icons.Default.PhotoLibrary, Icons.Default.ExpandMore, fraction.floatValue))
        } }
        var bounds = compose.onNodeWithTag("photo").getUnclippedBoundsInRoot()
        listOf(0f, .5f, 1f, .5f, 0f).forEachIndexed { index, amount ->
            compose.runOnIdle { fraction.floatValue = amount }
            compose.onNodeWithTag("photo").captureRoboImage("build/previews/polaroid-icon-blend-$index.png")
            assertEquals(bounds, compose.onNodeWithTag("photo").getUnclippedBoundsInRoot())
        }
        compose.onNodeWithTag("photo").performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }
}
