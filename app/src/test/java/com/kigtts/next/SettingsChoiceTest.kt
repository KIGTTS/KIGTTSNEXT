package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import android.graphics.BitmapFactory
import com.kigtts.next.theme.ThemeMode
import com.kigtts.next.theme.ThemeSettings
import com.kigtts.next.theme.ContrastLevel
import com.kigtts.next.theme.KigttsTheme
import com.kigtts.next.ui.settings.SettingChoiceGroup
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
class SettingsChoiceTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun rapidSelectionsRestoreTheSelectedCornersAndWidthsAfterThemeRecomposition() {
        val settings=mutableStateOf(ThemeSettings())
        compose.mainClock.autoAdvance=false
        compose.setContent { KigttsTheme(settings.value) {
            SettingChoiceGroup("对比度",ContrastLevel.entries,settings.value.contrast,{ it.label },
                { settings.value=settings.value.copy(contrast=it) },Modifier.width(320.dp))
        } }
        compose.waitForIdle()
        listOf(ContrastLevel.MEDIUM,ContrastLevel.HIGH,ContrastLevel.DEFAULT,ContrastLevel.MEDIUM).forEachIndexed { round,level ->
            val button=compose.onNodeWithContentDescription("对比度："+level.label)
            button.performTouchInput { down(center) }
            repeat(2) { compose.mainClock.advanceTimeByFrame();compose.waitForIdle() }
            button.performTouchInput { up() }
            repeat(40) { compose.mainClock.advanceTimeBy(32);compose.waitForIdle() }
            button.assertIsSelected()
            val file="build/previews/connected-restored-pill-$round.png"
            button.captureRoboImage(file)
            val bitmap=BitmapFactory.decodeFile(file)
            val density=compose.activity.resources.displayMetrics.density
            try {
                val y=(2*density).toInt();val edge=bitmap.getPixel((2*density).toInt(),y)
                val center=bitmap.getPixel(bitmap.width/2,y)
                assertNotEquals("Selected option must restore all rounded corners after release",edge,center)
            } finally { bitmap.recycle() }
            val widths=ContrastLevel.entries.map { item ->
                compose.onNodeWithContentDescription("对比度："+item.label).getUnclippedBoundsInRoot().let { (it.right-it.left).value }
            }
            assertTrue("Resting widths: $widths",widths.max()-widths.min()<1f)
        }
    }
    @Test fun shortPressCancelledBeforeExpansionFinishesAlwaysRestoresAllThreeWidths() {
        val value=mutableStateOf(ThemeMode.SYSTEM)
        compose.mainClock.autoAdvance=false
        compose.setContent { MaterialTheme {
            SettingChoiceGroup("主题模式",ThemeMode.entries,value.value,{ it.label },{ value.value=it },Modifier.width(320.dp))
        } }
        compose.waitForIdle()
        fun widths()=ThemeMode.entries.map { mode ->
            compose.onNodeWithContentDescription("主题模式："+mode.label).getUnclippedBoundsInRoot().let { (it.right-it.left).value }
        }
        val original=widths()
        repeat(6) { round ->
            val button=compose.onNodeWithContentDescription("主题模式："+ThemeMode.entries[round%3].label)
            button.performTouchInput { down(center) }
            repeat(3) { compose.mainClock.advanceTimeByFrame();compose.waitForIdle() }
            button.performTouchInput { cancel() }
            repeat(40) { compose.mainClock.advanceTimeBy(32);compose.waitForIdle() }
            val after=widths()
            original.indices.forEach { index -> assertEquals("Round $round, widths $after",original[index],after[index],.5f) }
        }
        compose.runOnIdle { assertEquals(ThemeMode.SYSTEM,value.value) }
    }
    @Test fun contrastTouchCommitsWhileThemeColorsAnimateAndTheGroupReturnsToEqualWidths() {
        val settings=mutableStateOf(ThemeSettings())
        compose.mainClock.autoAdvance=false
        compose.setContent { KigttsTheme(settings.value) {
            SettingChoiceGroup("对比度",ContrastLevel.entries,settings.value.contrast,{ it.label },
                { settings.value=settings.value.copy(contrast=it) },Modifier.width(320.dp).testTag("contrast"))
        } }
        compose.waitForIdle()
        val middle=compose.onNodeWithContentDescription("对比度：中")
        middle.performTouchInput { down(center) }
        repeat(5) { compose.mainClock.advanceTimeBy(32);compose.waitForIdle() }
        middle.performTouchInput { up() }
        repeat(30) { compose.mainClock.advanceTimeBy(32);compose.waitForIdle() }
        middle.assertIsSelected()
        compose.runOnIdle { assertEquals(ContrastLevel.MEDIUM,settings.value.contrast) }
        val widths=ContrastLevel.entries.map { level ->
            compose.onNodeWithContentDescription("对比度："+level.label).getUnclippedBoundsInRoot().let { (it.right-it.left).value }
        }
        assertTrue("Resting widths: $widths",widths.max()-widths.min()<1f)
        compose.onNodeWithTag("contrast").captureRoboImage("build/previews/connected-contrast-after-touch.png")
    }
    @Test fun connectedChoicesKeepExactlyOneSelectionAndDoNotClearTheCurrentOption() {
        val value=mutableStateOf(ThemeMode.SYSTEM);var changes=0
        compose.setContent { MaterialTheme {
            SettingChoiceGroup("主题模式",ThemeMode.entries,value.value,{ it.label },{ value.value=it;changes++ },Modifier.width(320.dp))
        } }
        compose.onAllNodes(isSelected()).assertCountEquals(1)
        val dark=compose.onNodeWithContentDescription("主题模式：深色")
        dark.performClick().assertIsSelected()
        compose.onAllNodes(isSelected()).assertCountEquals(1)
        dark.performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(ThemeMode.DARK,value.value);assertEquals(1,changes) }
        compose.onNodeWithContentDescription("主题模式：亮色").performClick().assertIsSelected()
        dark.assertIsNotSelected()
    }
    @Test fun pressExpandsTheNativeButtonAndCompressesItsNeighborWithoutSelecting() {
        val value=mutableStateOf(ThemeMode.SYSTEM)
        compose.mainClock.autoAdvance=false
        compose.setContent { MaterialTheme {
            SettingChoiceGroup("主题模式",ThemeMode.entries,value.value,{ it.label },{ value.value=it },Modifier.width(320.dp).testTag("choices"))
        } }
        compose.waitForIdle()
        fun width(label:String)=compose.onNodeWithContentDescription("主题模式：$label").getUnclippedBoundsInRoot().let { it.right-it.left }
        val light=compose.onNodeWithContentDescription("主题模式：亮色")
        val before=width("亮色");val neighbor=width("深色")
        compose.onNodeWithTag("choices").captureRoboImage("build/previews/settings-connected-rest.png")
        light.performTouchInput { down(center) }
        repeat(10) { compose.mainClock.advanceTimeBy(32);compose.waitForIdle() }
        assertTrue(width("亮色")>before);assertTrue(width("深色")<neighbor)
        compose.onNodeWithTag("choices").captureRoboImage("build/previews/settings-connected-pressed.png")
        light.performTouchInput { cancel() }
        repeat(25) { compose.mainClock.advanceTimeBy(32);compose.waitForIdle() }
        assertEquals(before,width("亮色"));compose.runOnIdle { assertEquals(ThemeMode.SYSTEM,value.value) }
    }
}
