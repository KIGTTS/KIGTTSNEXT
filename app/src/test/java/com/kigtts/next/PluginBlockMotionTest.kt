package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.MotionColumn
import com.kigtts.next.ui.plugins.PluginPageScreen
import com.kigtts.plugin.api.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk=[35],qualifiers="w393dp-h852dp-xxhdpi")
class PluginBlockMotionTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun step(frames:Int) { repeat(frames) { compose.mainClock.advanceTimeByFrame();compose.waitForIdle() } }
    @Test fun removalRetainsContainerForExitButImmediatelyDisablesActionsAndRapidReversalSettles() {
        val values=mutableStateOf(listOf("one","two"));var calls=0
        compose.mainClock.autoAdvance=false
        compose.setContent { MaterialTheme { MotionColumn(values.value,{ item,_->item },modifier=Modifier.width(300.dp).testTag("motion-items")) { item,active->
            Button({ calls++ },enabled=active,modifier=Modifier.fillMaxWidth().height(48.dp).testTag(item)) { Text(item) }
        } } }
        step(25)
        fun height()=compose.onNodeWithTag("motion-items").getUnclippedBoundsInRoot().let { it.bottom-it.top }
        val full=height()
        compose.onNodeWithTag("two").performTouchInput { down(center) }
        compose.runOnIdle { values.value=listOf("one") };step(3)
        compose.onNodeWithTag("two").assertIsNotEnabled()
        compose.onNodeWithTag("two").performTouchInput { up() }
        step(3)
        val middle=height();assertTrue("Middle $middle, full $full",middle<full);assertTrue("Middle $middle",middle>48.dp);assertEquals(0,calls)
        compose.runOnIdle { values.value=listOf("one","two") };step(25)
        compose.onNodeWithTag("two").assertIsEnabled();assertEquals(full,height())
        compose.runOnIdle { values.value=listOf("one") };step(25)
        compose.onNodeWithTag("two").assertDoesNotExist()
    }
    @Test fun declarativePluginUpdatesKeepTheSameActionAndRemovedNestedActionsCannotBeInvoked() {
        val text=mutableStateOf("初始内容");val action=PageAction("action","执行")
        val show=mutableStateOf(true);var calls=0
        compose.mainClock.autoAdvance=false
        compose.setContent { MaterialTheme { PluginPageScreen(PluginPage("sample","示例",listOf(
            PageBlock.Group("配置",listOf(PageBlock.Text(text.value),PageBlock.Actions(if(show.value)listOf(action)else emptyList()))),
            PageBlock.Group("配置",listOf(PageBlock.Notice("另一分组")))
        ))) { _,_->calls++ } } }
        step(30)
        compose.onNodeWithText("执行").performTouchInput { down(center) }
        compose.runOnIdle { text.value="更新内容" };step(5)
        compose.onAllNodesWithText("执行").assertCountEquals(1)
        compose.onNodeWithText("执行").performTouchInput { up() };step(15)
        assertEquals(1,calls)
        compose.runOnIdle { show.value=false };step(3)
        compose.onNodeWithText("执行").assertIsNotEnabled();step(25)
        compose.onNodeWithText("执行").assertDoesNotExist();compose.onNodeWithText("更新内容").assertExists()
    }
}
