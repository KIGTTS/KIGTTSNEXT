package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.theme.ThemeSettings
import com.kigtts.next.ui.components.ColorPickerDialog
import com.kigtts.next.ui.settings.ThemeColorSettings
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk=[35],qualifiers="zh-rCN-w393dp-h852dp-xxhdpi")
class ColorPickerTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun themeUsesSharedPresetsAndCancellingDoesNotChangeTheTheme() {
        val settings=mutableStateOf(ThemeSettings());var commits=0
        compose.setContent { MaterialTheme { ThemeColorSettings(settings.value) { settings.value=it;commits++ } } }
        compose.onNodeWithContentDescription("选择主题色").performClick()
        compose.onNodeWithContentDescription("预设颜色 #6750A4").performClick()
        compose.runOnIdle { assertEquals(0xFF00696E,settings.value.seed);assertEquals(0,commits) }
        compose.onNode(isDialog()).captureRoboImage("build/previews/shared-hsv-picker.png")
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithContentDescription("选择主题色").performClick()
        compose.onNodeWithContentDescription("预设颜色 #6750A4").performClick()
        compose.onNodeWithText("应用颜色").performClick()
        compose.runOnIdle { assertEquals(0xFF6750A4,settings.value.seed);assertEquals(1,commits) }
        compose.onNode(isDialog()).assertDoesNotExist()
    }
    @Test fun hsvHueEndpointDoesNotJumpToZeroThroughHexSynchronization() {
        var chosen=0L
        compose.setContent { MaterialTheme { ColorPickerDialog(0xFF00FF00,{ chosen=it },{}) } }
        val hue=compose.onNodeWithContentDescription("色相")
        hue.performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(360f) }
        hue.assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,ProgressBarRangeInfo(360f,0f..360f)))
        compose.onNodeWithContentDescription("明度").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(.5f) }
        compose.onNodeWithTag("color-preview").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"#800000"))
        compose.onNodeWithText("应用颜色").performClick()
        compose.runOnIdle { assertEquals(0xFF800000,chosen) }
    }
    @Test fun incompleteHexCannotBeCommittedAndNormalizedInputMatchesPreview() {
        var chosen=0L
        compose.setContent { MaterialTheme { ColorPickerDialog(0xFF00696E,{ chosen=it },{}) } }
        val field=compose.onNodeWithContentDescription("HEX 颜色")
        field.performTextReplacement("ABC")
        compose.onNodeWithText("应用颜色").assertIsNotEnabled()
        field.performTextReplacement("#80bfff")
        compose.onNodeWithText("应用颜色").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(0xFF80BFFF,chosen) }
    }
}
