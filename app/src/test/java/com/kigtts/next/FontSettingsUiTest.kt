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
import com.github.takahirom.roborazzi.captureRoboImage

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
        compose.onNodeWithTag("font-weight-control", useUnmergedTree = true).assertDoesNotExist()
        fun openWeight() {
            compose.onNodeWithTag("font-more/$variable").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("reveal-action/font/$variable/weight").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("reveal-action/font/$variable/weight").performClick()
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("font-more/$variable").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("可变字重").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("你好，KIGTTS · Aa 123").assertDoesNotExist()
        compose.onNodeWithText("可变字体").performClick()
        compose.onNode(isDialog()).captureRoboImage("build/previews/font-weight-dialog.png")
        compose.onNodeWithContentDescription("字重").performSemanticsAction(SemanticsActions.SetProgress) { it(700f) }
        compose.runOnIdle { Assert.assertEquals(400, settings.fontWeight) }
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { Assert.assertEquals(400, settings.fontWeight) }
        openWeight()
        compose.onNodeWithContentDescription("字重").performSemanticsAction(SemanticsActions.SetProgress) { it(700f) }
        compose.onNodeWithText("完成").performClick()
        compose.runOnIdle { Assert.assertEquals(700, settings.fontWeight) }
        openWeight()
        compose.onNodeWithText("恢复默认").performClick()
        compose.onNodeWithText("完成").performClick()
        compose.runOnIdle { Assert.assertEquals(200, settings.fontWeight) }
        compose.onNodeWithText("静态字体").performScrollTo().performClick()
        compose.onNodeWithTag("font-weight-control", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun anUnreadableFontCanBeDeletedAfterCancellingAndPreservesTheBackground() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val assets = AppearanceAssets(app)
        val key = "b".repeat(64) + ".ttf"
        // A zero-byte broken download cannot be memory-mapped by Robolectric's Windows Skia.
        File(app.filesDir, "appearance-assets/$key").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf()) }
        InstalledFonts(app).remember(key, "测试字体")
        val background = File(app.filesDir, "appearance-assets/" + "a".repeat(64) + ".jpg").apply { writeText("keep") }
        var settings by mutableStateOf(ThemeSettings(font = key, fontName = "测试字体"))
        compose.setContent { MaterialTheme { FontSettingsScreen(settings, { settings = it }) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("font-more/$key").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("font-more/$key").performClick()
        compose.onNodeWithTag("reveal-action/font/$key/delete").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.waitForIdle()
        Assert.assertTrue(assets.file(key)!!.isFile)
        compose.onNodeWithTag("font-more/$key").performClick()
        compose.onNodeWithTag("reveal-action/font/$key/delete").performClick()
        compose.onNodeWithText("删除").performClick()
        compose.waitForIdle()
        compose.waitUntil(10_000) { settings.font == null }
        compose.onNodeWithTag("font-more/$key").assertDoesNotExist()
        Assert.assertNull(assets.file(key))
        Assert.assertEquals("keep", background.readText())
        Assert.assertTrue(InstalledFonts(app).list(settings).isEmpty())
        Assert.assertThrows(IllegalArgumentException::class.java) { InstalledFonts(app).remove("../outside.ttf") }
        val valid = "c".repeat(64) + ".ttf"
        File(app.filesDir, "appearance-assets/$valid").writeBytes(javaClass.getResourceAsStream("/fixtures/appearance-font/fixture.ttf")!!.use { it.readBytes() })
        InstalledFonts(app).remember(valid, "有效字体")
        InstalledFonts(app).remove(valid)
        Assert.assertNull(assets.file(valid))
    }
}
