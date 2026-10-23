package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.theme.*
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.navigation.*
import com.kigtts.next.ui.settings.*
import com.kigtts.plugin.api.*
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
class PluginBadgeUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun entryBadgesUseThreeTonesUpdateWithoutMovingButtonsAndCapNumbers() {
        var count by mutableIntStateOf(99)
        var dark by mutableStateOf(false)
        var primary = 0
        compose.setContent { KigttsTheme(ThemeSettings(mode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
            primary = MaterialTheme.colorScheme.primary.toArgb()
            Surface { Column(Modifier.width(360.dp).padding(16.dp).testTag("badges"), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Row { PageActionButtons(listOf(ToolbarAction("updates", "我的插件", Icons.Default.Extension,
                    badge = PluginBadge(count, "可更新插件"), click = {}))) }
                ElasticButtonGroup(listOf(
                    ElasticAction("error", "AI出错", Icons.Default.Extension, {}, badge = PluginBadge(description = "AI任务出错", tone = BadgeTone.WARNING)),
                    ElasticAction("review", "待审核", Icons.Default.Extension, {}, badge = PluginBadge(description = "等待审核", tone = BadgeTone.CAUTION)),
                    ElasticAction("done", "完成", Icons.Default.Extension, {}, badge = PluginBadge(description = "任务完成"))), Modifier.width(140.dp), plain = true)
                SettingItem("插件商店", "", Icons.Default.Extension, SettingPosition.SINGLE, badge = PluginBadge(count, "设置更新提示"))
            } }
        } }
        compose.onAllNodesWithText("99").assertCountEquals(2)
        val bounds = compose.onNodeWithTag("updates").getUnclippedBoundsInRoot()
        compose.runOnIdle { count = 120 }
        compose.onAllNodesWithText("99+").assertCountEquals(2)
        compose.onNodeWithContentDescription("等待审核", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("AI任务出错", useUnmergedTree = true).assertExists()
        assertEquals(bounds, compose.onNodeWithTag("updates").getUnclippedBoundsInRoot())
        for (mode in listOf(false, true)) {
            compose.runOnIdle { dark = mode }; compose.waitForIdle()
            val file = File("build/previews/plugin-badges-$mode.png").apply { parentFile?.mkdirs() }
            compose.onNodeWithTag("badges").captureRoboImage(file.absolutePath)
            val dot = File("build/previews/plugin-badge-dot-$mode.png")
            compose.onNodeWithTag("done").captureRoboImage(dot.absolutePath)
            val pixels = android.graphics.BitmapFactory.decodeFile(dot.absolutePath)
            val area = (0 until pixels.width).sumOf { x -> (0 until pixels.height).count { y -> pixels.getPixel(x, y) == primary } }
            pixels.recycle()
            assertTrue("The final compact icon's dot must not be clipped by its rounded button", area >= 50)
        }
    }
}
