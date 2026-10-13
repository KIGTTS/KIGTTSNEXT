package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kigtts.next.ui.cards.CardAddFab
import com.kigtts.next.ui.navigation.LocalPageActive
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
class CardAddFabUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun tapFab()=compose.onNodeWithTag("card-add-fab").performTouchInput {click(center)}

    @Test fun busyLocksActionsWhileTheNativeMenuCanStillOpenAndRecoverWithoutAnotherTap() {
        var enabled by mutableStateOf(false);var cameras=0
        compose.setContent {MaterialTheme {Box(Modifier.size(360.dp,500.dp)) {CardAddFab(enabled,{cameras++},{},{})}}}
        tapFab();compose.onNodeWithTag("card-add-fab").assertIsOn().assertIsEnabled()
        compose.onNodeWithTag("card-add-action/0").assertIsNotEnabled().performTouchInput {click(center)}
        assertEquals(0,cameras)
        compose.runOnIdle {enabled=true}
        compose.onNodeWithTag("card-add-fab").assertIsOn()
        compose.onNodeWithTag("card-add-action/0").assertIsEnabled().performTouchInput {click(center)}
        assertEquals(1,cameras);compose.onNodeWithTag("card-add-fab").assertIsOff()
    }
    @Test fun nativeFabStaysAboveBrowserHitLayersAndDismissScrimLeavesDockInteractive() {
        var browserClicks=0;var dockClicks=0
        compose.setContent {MaterialTheme {Column(Modifier.width(360.dp)) {
            Box(Modifier.height(420.dp).fillMaxWidth()) {
                Box(Modifier.fillMaxSize().zIndex(1f).clickable {browserClicks++}.testTag("browser-underlay"))
                CardAddFab(true,{},{},{})
            }
            Box(Modifier.fillMaxWidth().height(56.dp).clickable {dockClicks++}.testTag("dock-outside-fab")) {Text("相册")}
        }}}
        tapFab();compose.onNodeWithTag("card-add-fab").assertIsOn()
        compose.onNodeWithTag("dock-outside-fab").performTouchInput {click(center)}
        assertEquals(1,dockClicks);assertEquals(0,browserClicks)
        compose.onNodeWithTag("card-add-fab").assertIsOn()
        compose.onNodeWithTag("dismiss-card-fab").performTouchInput {click(Offset(12f,12f))}
        compose.onNodeWithTag("card-add-fab").assertIsOff();assertEquals(0,browserClicks)
    }
    @Test fun leavingThePageClosesTheMenuAndReturnAllowsExactlyOneGalleryAction() {
        var active by mutableStateOf(true);var galleries=0
        compose.setContent {CompositionLocalProvider(LocalPageActive provides active) {MaterialTheme {
            Box(Modifier.size(360.dp,500.dp)) {CardAddFab(true,{},{galleries++},{})}
        }}}
        tapFab();compose.onNodeWithTag("card-add-fab").assertIsOn()
        compose.runOnIdle {active=false}
        compose.onNodeWithTag("card-add-fab").assertIsOff().assertIsNotEnabled()
        tapFab();compose.onNodeWithTag("card-add-fab").assertIsOff()
        compose.runOnIdle {active=true}
        tapFab();compose.onNodeWithTag("card-add-action/1").performTouchInput {click(center)}
        assertEquals(1,galleries);compose.onNodeWithTag("card-add-fab").assertIsOff()
    }
    @Test fun repeatedRealTapsCanReverseNativeExpansionBeforeItsAnimationFinishes() {
        var actions=0
        compose.setContent {MaterialTheme {Box(Modifier.size(360.dp,500.dp)) {CardAddFab(true,{actions++},{actions++},{actions++})}}}
        compose.mainClock.autoAdvance=false
        repeat(8) {index->
            tapFab()
            // Robolectric's paused Android looper does not flush global snapshot writes by itself.
            compose.runOnIdle { Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeBy(32);compose.waitForIdle()
            if(index%2==0)compose.onNodeWithTag("card-add-fab").assertIsOn()
            else compose.onNodeWithTag("card-add-fab").assertIsOff()
        }
        compose.mainClock.autoAdvance=true;compose.waitForIdle()
        assertEquals(0,actions);tapFab();compose.onNodeWithTag("card-add-fab").assertIsOn()
    }
}
