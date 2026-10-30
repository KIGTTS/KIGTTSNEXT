package com.kigtts.next

import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.state.*
import com.kigtts.next.theme.*
import com.kigtts.next.ui.settings.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class AboutScreenUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun settingsEntryOpensAboutAndItsLinksRemainReachable() {
        var destination by mutableStateOf<Destination?>(null)
        val urls = mutableListOf<String>()
        val documents = mutableListOf<SettingsCategory>()
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Surface(Modifier.fillMaxSize()) {
                if (destination == null) SettingsScreen({ destination = it })
                else AboutScreen(openLink = urls::add, openDocument = documents::add, version = "0.1.0")
            }
        } }
        compose.onNodeWithTag("settings-entry/about").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(Destination.SettingsDetail(SettingsCategory.ABOUT), destination) }
        compose.onNodeWithText("版本 0.1.0").assertIsDisplayed()
        compose.onNodeWithText("LHTSTUDIO").assertDoesNotExist()
        assertEquals("关于", SettingsCategory.ABOUT.title)
        listOf("用户协议", "隐私政策", "开源许可证", "项目主页", "反馈问题").forEach { title ->
            compose.onNode(hasText(title) and hasClickAction()).performScrollTo().performClick()
        }
        compose.runOnIdle {
            assertEquals(listOf(ABOUT_PROJECT_URL, "$ABOUT_PROJECT_URL/issues"), urls)
            assertEquals(listOf(SettingsCategory.USER_AGREEMENT, SettingsCategory.PRIVACY_POLICY,
                SettingsCategory.OPEN_SOURCE_LICENSES), documents)
        }
    }

    @Test fun searchRoutesDirectlyToTheAboutOption() {
        var destination: Destination? = null
        compose.setContent { KigttsTheme(ThemeSettings()) {
            SettingsSearchDialog(emptyList(), {}, { destination = it })
        } }
        compose.onNodeWithTag("settings-search-input").performTextInput("开源许可证")
        compose.onNode(hasText("设置 / 关于 / 协议与隐私 / 开源许可证") and hasClickAction()).performClick()
        compose.runOnIdle {
            assertEquals(Destination.SettingsDetail(SettingsCategory.OPEN_SOURCE_LICENSES), destination)
            assertEquals("开源许可证", SettingFocus.target?.title)
            SettingFocus.target = null
        }
    }

    @Test fun tappingChangesTheBackgroundFlowersButKeepsTheWordmarkInEitherTheme() {
        var mode by mutableStateOf(ThemeMode.LIGHT)
        compose.setContent { KigttsTheme(ThemeSettings(mode = mode)) {
            Surface(Modifier.fillMaxSize()) { AboutScreen({}, {}, version = "0.1.0") }
        } }
        val hero = compose.onNodeWithTag("about-easter-egg")
        val logo = compose.onNodeWithTag("about-logo", useUnmergedTree = true)
        val restingBounds = logo.getUnclippedBoundsInRoot()
        hero.captureRoboImage("build/previews/about-flowers-resting.png")
        val before = BitmapFactory.decodeFile("build/previews/about-flowers-resting.png")
        hero.performTouchInput { click() }
        compose.waitForIdle()
        hero.captureRoboImage("build/previews/about-flowers-moving.png")
        val moving = BitmapFactory.decodeFile("build/previews/about-flowers-moving.png")
        var changed = 0
        for (y in 0 until before.height step 3) for (x in 0 until before.width step 3) {
            if (before.getPixel(x, y) != moving.getPixel(x, y)) changed++
        }
        assertTrue("A tap must visibly change the decorative silhouettes: $changed pixels changed", changed > 100)
        assertEquals(restingBounds, logo.getUnclippedBoundsInRoot())
        compose.onNodeWithContentDescription("KIGTTS 横版字标").assertExists()
        // Repeated clicks keep the same wordmark and layout.
        hero.performClick()
        hero.performClick()
        compose.waitForIdle()
        assertEquals(restingBounds, logo.getUnclippedBoundsInRoot())
        compose.onRoot().captureRoboImage("build/previews/about-light.png")
        compose.runOnIdle { mode = ThemeMode.DARK }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/previews/about-dark.png")
        compose.onNodeWithText("开源许可证").assertIsDisplayed()
    }

    @Test fun licenseDialogDisplaysTheBundledLicenseWithoutOpeningAWebsite() {
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Surface(Modifier.fillMaxSize()) { AboutLicensesScreen() }
        } }
        compose.onNodeWithText("KIGTTS · MIT License").performClick()
        compose.waitUntil(5000) {
            compose.onAllNodes(hasText("Permission is hereby granted", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("关闭").performClick()
        compose.onNode(hasText("Permission is hereby granted", substring = true)).assertDoesNotExist()
    }
}
