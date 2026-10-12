package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kigtts.next.theme.*
import com.kigtts.next.ui.navigation.PageScaffold
import com.kigtts.next.ui.navigation.RegisterPageActions
import com.kigtts.next.ui.navigation.ToolbarAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
@Config(sdk=[35],qualifiers="zh-rCN-w393dp-h852dp-xxhdpi")
class PageLoadingMotionTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun step(ms:Long) { repeat(((ms+15)/16).toInt()) { compose.mainClock.advanceTimeByFrame();compose.waitForIdle() } }
    @Test fun collapsedAndSmallBarsKeepLoadingAfterTheTitle() {
        var large by mutableStateOf(true)
        compose.mainClock.autoAdvance=false
        compose.setContent { KigttsTheme(ThemeSettings()) { PageScaffold("插件页面",{},largeBar=large) {
            com.kigtts.next.ui.navigation.RegisterPageLoading(true)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("scroll-page")) { repeat(40) { Text("条目 $it",Modifier.height(64.dp)) } }
        } } }
        step(600)
        compose.onNodeWithTag("scroll-page").performTouchInput { swipeUp() };step(800)
        fun check() {
            val ring=compose.onNodeWithTag("page-loading").getUnclippedBoundsInRoot()
            val title=compose.onNodeWithTag("page-title").getUnclippedBoundsInRoot()
            assertTrue("ring=$ring title=$title",ring.left>=title.right)
            assertTrue("ring=$ring title=$title",kotlin.math.abs((ring.top+ring.bottom).value-(title.top+title.bottom).value)<6f)
        }
        check();compose.runOnIdle { large=false };step(600);check()
    }
    @Test fun changingToolbarActionsAnimatesWidthAndRemovedActionsCannotExecute() {
        var extra by mutableStateOf(false);var clicked=0
        compose.setContent { KigttsTheme(ThemeSettings()) { PageScaffold("插件页面",{}) {
            RegisterPageActions(if(extra)listOf(ToolbarAction("extra","额外操作",Icons.Default.Add){clicked++})else emptyList())
        } } }
        compose.mainClock.autoAdvance=false
        compose.runOnIdle { extra=true };step(16)
        repeat(12) {
            val node=compose.onAllNodesWithTag("page-action/extra").fetchSemanticsNodes().firstOrNull()
            if(node==null || node.boundsInRoot.width==0f)step(16)
        }
        val entering=compose.onNodeWithTag("page-action/extra").getUnclippedBoundsInRoot()
        step(500);val full=compose.onNodeWithTag("page-action/extra").getUnclippedBoundsInRoot()
        assertTrue("entering=$entering full=$full",(entering.right-entering.left).value>0f&&(entering.right-entering.left)<(full.right-full.left))
        compose.onNodeWithTag("extra").performClick();assertEquals(1,clicked)
        compose.runOnIdle { extra=false };step(16)
        repeat(12) { if(compose.onNodeWithTag("page-action/extra").getUnclippedBoundsInRoot().right-compose.onNodeWithTag("page-action/extra").getUnclippedBoundsInRoot().left==full.right-full.left)step(16) }
        compose.onNodeWithTag("extra").assertIsNotEnabled()
        val leaving=compose.onNodeWithTag("page-action/extra").getUnclippedBoundsInRoot()
        assertTrue((leaving.right-leaving.left)<(full.right-full.left))
        step(500);compose.onNodeWithTag("extra").assertDoesNotExist();assertEquals(1,clicked)
        compose.mainClock.autoAdvance=true
    }
    @Test fun loadingRingAppearsToTheRightOfBackAndDoesNotMoveTheList() {
        var busy by mutableStateOf(false)
        val page=PluginPage("packs","语音包",emptyList(),ManagementList("loading",listOf(ManagementItem("a","语音包"))))
        compose.setContent { KigttsTheme(ThemeSettings()) { PageScaffold("语音包",{}) { PluginPageScreen(page,busy,invoke={_,_->}) } } }
        val before=compose.onNodeWithTag("managed-item/a").getUnclippedBoundsInRoot()
        compose.mainClock.autoAdvance=false
        compose.runOnIdle { busy=true };step(96)
        compose.onNodeWithTag("page-loading",useUnmergedTree=true).assertExists("Loading tree: "+compose.onRoot().printToString())
        val ring=compose.onNodeWithTag("page-loading",useUnmergedTree=true).getUnclippedBoundsInRoot()
        val back=compose.onNodeWithTag("page-back").getUnclippedBoundsInRoot()
        assertTrue("ring=$ring back=$back",ring.left>=back.right)
        assertEquals(before,compose.onNodeWithTag("managed-item/a").getUnclippedBoundsInRoot())
        step(300);compose.runOnIdle { busy=false };step(64)
        compose.onNodeWithTag("page-loading").assertExists()
        step(500);compose.onNodeWithTag("page-loading").assertDoesNotExist()
        assertEquals(before,compose.onNodeWithTag("managed-item/a").getUnclippedBoundsInRoot())
        compose.mainClock.autoAdvance=true
    }
}
