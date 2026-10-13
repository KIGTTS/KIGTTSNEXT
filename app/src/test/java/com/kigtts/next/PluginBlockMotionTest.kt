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
import com.kigtts.next.ui.navigation.LocalPageActive
import com.github.takahirom.roborazzi.captureRoboImage
import android.graphics.BitmapFactory
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
    @Test fun pluginChoicesAndSwitchesDoNotFlashDisabledColoursDuringRequests() {
        var busy by mutableStateOf(false)
        var calls = 0
        val page = PluginPage("p", "设置", listOf(
            PageBlock.Toggle("保持加载", true, "resident"),
            PageBlock.Choice("方式", listOf(PageAction("on", "开启"), PageAction("off", "关闭")), "on")))
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme { PluginPageScreen(page, busy) { _, _ -> calls++ } } }
        step(30)
        fun colour(description: String, name: String): Int {
            val path = "build/previews/plugin-request-$name.png"
            compose.onNodeWithContentDescription(description).captureRoboImage(path)
            return BitmapFactory.decodeFile(path).let { bitmap ->
                bitmap.getPixel(bitmap.width / 2, bitmap.height / 4).also { bitmap.recycle() }
            }
        }
        val descriptions = listOf("保持加载", "方式：开启", "方式：关闭")
        val before = descriptions.mapIndexed { i, label -> colour(label, "before-$i") }
        compose.runOnIdle { busy = true }; step(20)
        descriptions.forEachIndexed { i, label ->
            compose.onNodeWithContentDescription(label).assertIsNotEnabled().performTouchInput { click() }
            assertEquals(before[i], colour(label, "busy-$i"))
        }
        assertEquals(0, calls)
        compose.runOnIdle { busy = false }; step(20)
        descriptions.forEach { compose.onNodeWithContentDescription(it).assertIsEnabled() }
        compose.onNodeWithContentDescription("方式：关闭").performClick()
        assertEquals(1, calls)
    }
    @Test fun preloadedRowsAreVisibleImmediatelyButLaterInsertionsStillAnimate() {
        val values=mutableStateOf(listOf("one","two"))
        compose.mainClock.autoAdvance=false
        compose.setContent { MaterialTheme { MotionColumn(values.value,{ item,_->item },animateInitial=false,
            modifier=Modifier.width(300.dp).testTag("preloaded-items")) { item,active ->
            Button({},enabled=active,modifier=Modifier.fillMaxWidth().height(48.dp).testTag(item)) { Text(item) }
        } } }
        compose.waitForIdle()
        compose.onNodeWithTag("one").assertIsDisplayed();compose.onNodeWithTag("two").assertIsDisplayed()
        fun height()=compose.onNodeWithTag("preloaded-items").getUnclippedBoundsInRoot().let { it.bottom-it.top }
        val first=height();step(20);assertEquals(first,height())
        compose.runOnIdle { values.value+= "three" };step(5)
        val middle=height();step(20)
        assertTrue(middle>first);assertTrue(middle<height())
        compose.onNodeWithTag("three").assertIsDisplayed()
    }
    @Test fun pluginNestedRowsRemainAtFullSizeOnFirstFrameAndAfterReentering() {
        val revision=mutableStateOf(0)
        val page=PluginPage("page","页面",listOf(PageBlock.Group("配置",listOf(PageBlock.Actions(
            listOf(PageAction("one","第一项"),PageAction("two","第二项")))))))
        compose.mainClock.autoAdvance=false
        compose.setContent { MaterialTheme { key(revision.value) { PluginPageScreen(page) {_,_->} } } }
        repeat(2) { index ->
            if(index>0)compose.runOnIdle { revision.value++ }
            compose.waitForIdle();compose.onNodeWithText("第二项").assertIsDisplayed()
            val first=compose.onNodeWithText("第二项").getUnclippedBoundsInRoot()
            step(20)
            assertEquals(first,compose.onNodeWithText("第二项").getUnclippedBoundsInRoot())
        }
    }
    @Test fun temporaryBusyAndPageLocksKeepButtonColourWhileBlockingClicks() {
        var busy by mutableStateOf(false);var active by mutableStateOf(true);var calls=0
        val page=PluginPage("p","插件",listOf(PageBlock.Actions(listOf(PageAction("go","执行")))))
        compose.mainClock.autoAdvance=false
        compose.setContent { MaterialTheme { CompositionLocalProvider(LocalPageActive provides active) { PluginPageScreen(page,busy) {_,_->calls++} } } }
        step(30)
        fun color(name:String):Int {
            val path="build/previews/plugin-lock-$name.png";compose.onNodeWithText("执行").captureRoboImage(path)
            return BitmapFactory.decodeFile(path).let { image -> image.getPixel(image.width/2,image.height/4).also {image.recycle()} }
        }
        val before=color("before")
        compose.runOnIdle { busy=true };step(20);compose.onNodeWithText("执行").assertIsNotEnabled();assertEquals(before,color("busy"))
        compose.runOnIdle { busy=false;active=false };step(20);compose.onNodeWithText("执行").assertIsNotEnabled();assertEquals(before,color("background"))
        compose.runOnIdle { active=true };step(20);compose.onNodeWithText("执行").performTouchInput { down(center);advanceEventTime(8);up() };step(40)
        assertEquals(1,calls);assertEquals(before,color("restored"))
    }
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
