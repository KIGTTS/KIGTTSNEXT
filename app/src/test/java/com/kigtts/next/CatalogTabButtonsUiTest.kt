package com.kigtts.next

import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.theme.*
import com.kigtts.next.ui.plugins.CatalogTabs
import com.kigtts.plugin.api.ManagementFilter
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
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xhdpi")
class CatalogTabButtonsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun temporaryRequestLocksKeepTheButtonColorsInBothThemesAndRetainSelection() {
        var enabled by mutableStateOf(true)
        var dark by mutableStateOf(false)
        var selected by mutableStateOf("all")
        val calls = mutableListOf<String>()
        compose.setContent { key(dark) { KigttsTheme(ThemeSettings(mode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT), publishWebTheme = true) {
            Box(Modifier.width(360.dp)) {
                CatalogTabs(listOf(ManagementFilter("all", "全部", "all", selected == "all"),
                    ManagementFilter("audio", "声音", "audio", selected == "audio")), enabled) { action, _ ->
                    calls += action; selected = action
                }
            }
        } } }
        fun image(label: String): IntArray {
            val file = File("build/previews/catalog-buttons-$label.png").apply { parentFile?.mkdirs() }
            compose.onNodeWithTag("catalog-tabs").captureRoboImage(file.absolutePath)
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            return try { IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) } }
            finally { bitmap.recycle() }
        }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme; enabled = true }
            compose.waitForIdle()
            val normal = image("$theme-normal")
            compose.runOnIdle { enabled = false }; compose.waitForIdle()
            compose.onNodeWithContentDescription("：全部", substring = true).assertIsNotEnabled().assertIsSelected()
            assertArrayEquals("Busy state must not flash disabled colors", normal, image("$theme-locked"))
        }
        compose.runOnIdle { enabled = true }
        compose.onNodeWithContentDescription("：声音", substring = true).performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(listOf("audio"), calls)
        compose.onNodeWithContentDescription("：声音", substring = true).assertIsSelected()
    }
}
