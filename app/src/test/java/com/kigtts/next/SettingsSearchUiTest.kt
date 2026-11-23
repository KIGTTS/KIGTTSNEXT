package com.kigtts.next

import android.graphics.BitmapFactory
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.state.*
import com.kigtts.next.theme.*
import com.kigtts.next.ui.settings.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class SettingsSearchUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun searchHighlightPaintsTheRowWithoutFillingItsRoundedCorners() {
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
            SettingItem("背景图片", "", null, SettingPosition.SINGLE, modifier = Modifier.testTag("highlight-row"),
                corners = RoundedCornerShape(24.dp))
        } }
        compose.mainClock.autoAdvance = false
        fun pixels(name: String): Pair<Int, Int> {
            val path = "build/previews/$name.png"
            compose.onNodeWithTag("highlight-row").captureRoboImage(path)
            val bitmap = BitmapFactory.decodeFile(path)
            return try { bitmap.getPixel(2, 2) to bitmap.getPixel(bitmap.width / 2, 2) }
            finally { bitmap.recycle() }
        }
        val before = pixels("settings-highlight-idle")
        try {
            compose.runOnIdle {
                SettingFocus.target = SettingFocus.Target("背景图片")
                Snapshot.sendApplyNotifications()
            }
            var highlighted = false
            repeat(10) { frame ->
                shadowOf(Looper.getMainLooper()).idleFor(80, TimeUnit.MILLISECONDS)
                compose.mainClock.advanceTimeBy(80)
                val active = pixels("settings-highlight-$frame")
                assertEquals(before.first, active.first)
                highlighted = highlighted || before.second != active.second
            }
            assertTrue("The visible area of the setting row should highlight", highlighted)
        } finally {
            compose.runOnIdle { SettingFocus.target = null }
        }
    }

    @Test fun focusedSearchShowsTheFullPathAndTargetsTheActualOption() {
        var destination: Destination? = null; var visible by mutableStateOf(true)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            if (visible) SettingsSearchDialog(emptyList(), { visible = false }, { destination = it })
        } }
        compose.onNodeWithTag("settings-search-input").assertIsFocused().performTextInput("背景图片")
        compose.onNodeWithText("设置 / 外观 / 背景与字体 / 背景图片").assertIsDisplayed()
        compose.onNode(hasText("背景图片") and hasClickAction() and hasText("设置 / 外观 / 背景与字体 / 背景图片")).performClick()
        assertEquals(Destination.SettingsDetail(SettingsCategory.APPEARANCE), destination)
        assertEquals("背景图片", SettingFocus.target?.title)
        assertFalse(visible); SettingFocus.target = null
    }
}
