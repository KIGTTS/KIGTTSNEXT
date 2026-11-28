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
import com.kigtts.next.i18n.LocalUiLocale
import com.kigtts.next.state.*
import com.kigtts.next.theme.*
import com.kigtts.next.ui.settings.*
import com.kigtts.next.updates.OFFICIAL_WEBSITE
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
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-entry/about"))
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

    @Test fun aboutPageRefreshesWhenTheAppLanguageChanges() {
        var locale by mutableStateOf("en")
        compose.setContent { CompositionLocalProvider(LocalUiLocale provides locale) {
            KigttsTheme(ThemeSettings()) { AboutPage({}, {}, {}, version = "0.1.0") }
        } }
        compose.onNodeWithText("About").assertIsDisplayed()
        compose.onNodeWithText("Version 0.1.0").assertIsDisplayed()
        compose.onNode(hasText("Privacy policy", ignoreCase = true) and hasClickAction()).performScrollTo().assertIsDisplayed()

        compose.runOnIdle { locale = "ko" }
        compose.onNodeWithText("앱 정보").assertIsDisplayed()
        compose.onNodeWithText("버전 0.1.0").assertExists()
        compose.onNode(hasText("개인정보 처리방침") and hasClickAction()).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("隐私政策").assertDoesNotExist()
    }

    @Test fun brandAndSupportRowGoToTheSameOfficialWebsite() {
        val urls = mutableListOf<String>()
        compose.setContent { KigttsTheme(ThemeSettings()) { AboutPage({}, urls::add, {}) } }
        compose.onNodeWithTag("about-logo").performClick()
        compose.onNode(hasText("访问官网") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithTag("about-mascot").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(OFFICIAL_WEBSITE, OFFICIAL_WEBSITE), urls) }
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

    @Test fun tappingOneFlowerChangesOnlyThatFlowerAndKeepsTheWordmarkAndLinks() {
        var mode by mutableStateOf(ThemeMode.LIGHT)
        compose.setContent { KigttsTheme(ThemeSettings(mode = mode)) {
            AboutPage({}, {}, {}, version = "0.1.0")
        } }
        fun states() = (0..5).map { compose.onNodeWithTag("about-flower/$it").fetchSemanticsNode().config[
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription] }
        val logo = compose.onNodeWithTag("about-logo", useUnmergedTree = true)
        val restingBounds = logo.getUnclippedBoundsInRoot()
        val before = states()
        compose.onNodeWithTag("about-flower/2").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.waitForIdle()
        val after = states()
        (0..5).forEach { if (it == 2) assertNotEquals(before[it], after[it]) else assertEquals(before[it], after[it]) }
        // A real tap on unused toolbar/background space reaches the backdrop rather than
        // changing all flowers, and no transparent overlay can swallow the actual controls.
        compose.onNodeWithTag("immersive-page").performTouchInput {
            click(androidx.compose.ui.geometry.Offset(width * .08f, height * .20f))
        }
        compose.waitForIdle()
        assertNotEquals(after[0], states()[0])
        assertEquals(after[2], states()[2])
        assertEquals(restingBounds, logo.getUnclippedBoundsInRoot())
        compose.onRoot().captureRoboImage("build/previews/about-light.png")
        compose.onNode(hasText("用户协议") and hasClickAction()).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { mode = ThemeMode.DARK }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/previews/about-dark.png")
        compose.onNodeWithContentDescription("KIGTTS 横版字标").assertExists()
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
