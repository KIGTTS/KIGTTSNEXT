package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kigtts.next.theme.ThemeSettings
import com.kigtts.next.theme.AppearanceAssets
import com.kigtts.next.theme.InstalledFonts
import android.app.Application
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ApplicationProvider
import java.io.File
import com.kigtts.next.ui.settings.FontSettingsScreen
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FontSettingsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun onlyAnAvailableStoreGetsDownloadGuidance() {
        var available by mutableStateOf(false)
        var calls = 0
        compose.setContent { MaterialTheme {
            FontSettingsScreen(ThemeSettings(), {}, if (available) ({ calls++; Unit }) else null)
        } }
        compose.onNodeWithText("从商店获取").assertDoesNotExist()
        compose.onNodeWithText("导入字体").assertExists()
        compose.runOnIdle { available = true }
        compose.onNodeWithText("从商店获取").performClick()
        compose.runOnIdle { Assert.assertEquals(1, calls); available = false }
        compose.onNodeWithText("从商店获取").assertDoesNotExist()
    }

    @Test fun onlyVariableFontsShowLiveWeightControls() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        fun install(folder: String, name: String): String {
            val file = File(app.cacheDir, "$folder.ttf").apply {
                writeBytes(this@FontSettingsUiTest.javaClass.getResourceAsStream("/fixtures/$folder/fixture.ttf")!!.use { it.readBytes() })
            }
            return AppearanceAssets(app).importFont(Uri.fromFile(file)).also { InstalledFonts(app).remember(it, name) }
        }
        val variable = install("variable-font", "可变字体"); install("appearance-font", "静态字体")
        var settings by mutableStateOf(ThemeSettings(font = variable, fontName = "可变字体", fontWeight = 400))
        compose.setContent { MaterialTheme { FontSettingsScreen(settings, { settings = it }) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("font-weight-control").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("字重").performSemanticsAction(SemanticsActions.SetProgress) { it(700f) }
        compose.runOnIdle { Assert.assertEquals(700, settings.fontWeight) }
        compose.onNodeWithText("恢复默认").performClick()
        compose.runOnIdle { Assert.assertEquals(200, settings.fontWeight) }
        compose.onNodeWithText("静态字体").performScrollTo().performClick()
        compose.onNodeWithTag("font-weight-control").assertDoesNotExist()
    }
}
