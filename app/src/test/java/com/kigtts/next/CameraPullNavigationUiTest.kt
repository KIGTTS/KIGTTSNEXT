package com.kigtts.next

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.*
import com.kigtts.next.theme.*
import com.kigtts.next.ui.navigation.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk=[35],qualifiers="zh-rCN-w393dp-h852dp-xxhdpi")
class CameraPullNavigationUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private class Fixture(parent:Destination=Destination.Cards,side:Boolean=false, val header:Boolean=false, large:Boolean=true) {
        var state by mutableStateOf(AppState(destination=if(side)Destination.Home else parent,
            navigationStack=if(side)emptyList() else listOf(Destination.Home),sidePage=if(side)SidePage.CARDS else null))
        var cameraGuard by mutableStateOf(false);var printerGuard by mutableStateOf(false)
        var slider by mutableStateOf(false);var sliderDistance=0f
        var nativeViewfinder by mutableStateOf(false);var exposureControl by mutableStateOf(false)
        var largeBar by mutableStateOf(large);var headerActions=0
        var opens=0;var backs=0
        lateinit var pull:CameraPullState
        val parentIdentity=if(side)"1/side/cards" else if(parent==Destination.Cards)"1/cards" else "1/card-editor/existing"
        fun open():String {opens++;val id="pull-$opens";state=state.copy(destination=Destination.CardCamera(id),navigationStack=state.navigationStack+state.destination);return id}
        fun back() {backs++;val stack=state.navigationStack;state=state.copy(destination=stack.last(),navigationStack=stack.dropLast(1))}
        val cameraIdentity get()="2/card-camera/pull-$opens"
    }
    private fun show(parent:Destination=Destination.Cards,side:Boolean=false,header:Boolean=false,large:Boolean=true):Fixture {
        val f=Fixture(parent,side,header,large)
        compose.setContent {KigttsTheme(ThemeSettings()) {
            val navigation=rememberPageNavigation(f.state);val current=navigation.current
            val scope=rememberCoroutineScope();val pull=remember {CameraPullState(scope,f::open,f::back,
                {(f.state.destination as? Destination.CardCamera)?.requestId})}
            SideEffect { f.pull=pull }
            val latest=rememberUpdatedState(navigation)
            val back=rememberPredictiveReturn(current.cameraRequestId!=null,current.identity,f::back)
            val latestProgress=rememberUpdatedState(back.progress)
            val camera=navigation.pages.lastOrNull {it.cameraRequestId!=null&&(it==current||it.depth==current.depth+1&&it !in navigation.wanted)}
            val covered=camera?.let {navigation.wanted.lastOrNull {entry->entry.depth==it.depth-1}}
            CompositionLocalProvider(LocalCameraPullState provides pull) {
                RegisterCameraPullGuard(f.cameraGuard);RegisterCameraPullGuard(f.printerGuard)
                Box(Modifier.size(360.dp,640.dp).testTag("camera-pull-root").cameraPullNavigation(pull) {
                    val nav=latest.value;val foreground=nav.current
                    if(nav.outgoingAbove||latestProgress.value>0f)null
                    else if(foreground.isCardsBrowser)CameraPullOrigin(CameraPullMode.ENTER,foreground.identity)
                    else foreground.cameraRequestId?.let {CameraPullOrigin(CameraPullMode.EXIT,f.parentIdentity,it)}
                }) {
                    navigation.pages.forEach {entry->key(entry) {
                        val foreground=entry==current
                        val role=if(foreground)PageRole.FOREGROUND else if(entry in navigation.wanted)PageRole.BACKGROUND else PageRole.EXITING
                        val pose=if(pull.active&&(entry.cameraRequestId==pull.cameraRequestId||entry.identity==pull.parentIdentity))
                            remember(pull,entry) {CameraPagePose(pull,entry.cameraRequestId!=null)} else null
                        PageMotion(entry,role,foreground&&!navigation.outgoingAbove,if(foreground)back.progress else 0f,back.edge,
                            if(entry==navigation.wanted.getOrNull(navigation.wanted.lastIndex-1))back.progress else 0f,
                            {navigation.retention.retire(entry)},vertical=entry==camera||entry==covered,cameraPose=pose) {
                            val body: @Composable () -> Unit = { Box(Modifier.fillMaxSize().background(if(entry.cameraRequestId!=null)Color.Blue else Color.Green)
                                .then(if(f.slider)Modifier.testTag("fixture-native-slider").draggable(rememberDraggableState {f.sliderDistance+=it},Orientation.Vertical)
                                else Modifier.clickable { /* Clickable down is deliberately consumed. */ })) {
                                if(entry.cameraRequestId!=null && f.nativeViewfinder) Box(Modifier.fillMaxSize()
                                    .cameraPullViewfinder().pointerInput(Unit) {
                                        awaitEachGesture {
                                            awaitFirstDown(requireUnconsumed=false).consume()
                                            do {val event=awaitPointerEvent();event.changes.forEach {it.consume()}} while(event.changes.any {it.pressed})
                                        }
                                    })
                                if(entry.cameraRequestId!=null && f.exposureControl) Box(Modifier.align(Alignment.Center)
                                    .size(48.dp,160.dp).excludeCameraPull()
                                    .draggable(rememberDraggableState {f.sliderDistance+=it},Orientation.Vertical))
                            } }
                            if(f.header && entry.cameraRequestId==null) PageScaffold("拍立得",f::back,largeBar=f.largeBar,subtitle="下滑进行拍照",
                                actions=listOf(ToolbarAction("fixture-header-add","新建",Icons.Default.Add) {f.headerActions++}),content=body)
                            else body()
                        }
                    }}
                }
            }
        }}
        compose.waitForIdle();compose.mainClock.autoAdvance=false
        return f
    }
    private fun layer(identity:String)=compose.onNodeWithTag("page-layer/$identity")
    private fun top(identity:String)=layer(identity).getUnclippedBoundsInRoot().top.value
    private fun step(time:Long) {
        compose.runOnIdle { Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(time);compose.waitForIdle()
    }
    private fun beginDrag(start:Float,end:Float,x:Float=.5f) {
        compose.onNodeWithTag("camera-pull-root").performTouchInput {
            down(Offset(width*x,height*start));moveTo(Offset(width*x,height*end),delayMillis=160)
        };step(16)
    }
    private fun release() {compose.onNodeWithTag("camera-pull-root").performTouchInput {advanceEventTime(600);up()}}
    private fun rest() {step(700);compose.mainClock.autoAdvance=true;compose.waitForIdle()}
    private fun dragHeader(tag:String,leftTitle:Boolean=false) {
        val bounds=compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        val startX=if(leftTitle)bounds.left.value+8f else (bounds.left.value+bounds.right.value)/2f
        val startY=(bounds.top.value+bounds.bottom.value)/2f
        if(leftTitle)assertTrue("The gesture begins in the title outside the central safe region",startX<360f*.23f)
        compose.onNodeWithTag("camera-pull-root").performTouchInput {
            val start=Offset(width*startX/360f,height*startY/640f)
            down(start);moveTo(start+Offset(0f,height*.45f),delayMillis=160)
        };step(16)
    }

    @Test fun leftAlignedLargeTitleCanPullIntoTheCameraWhileBackAndHeaderActionsKeepTheirGestures() {
        val f=show(header=true)
        dragHeader("page-back");release();step(32)
        assertEquals(0,f.opens);assertEquals(0,f.backs)
        dragHeader("fixture-header-add");release();step(32)
        assertEquals(0,f.opens);assertEquals(0,f.headerActions)
        compose.onNodeWithTag("fixture-header-add").performClick();step(32)
        assertEquals(1,f.headerActions)
        dragHeader("page-title",leftTitle=true);assertEquals(1,f.opens)
        release();rest();assertTrue(f.state.destination is Destination.CardCamera)
        assertEquals(0f,top(f.cameraIdentity),.2f);assertEquals(0,f.backs)
    }
    @Test fun compactTitleRegistrationFollowsItsActualPositionAfterTheHeaderChangesSize() {
        val f=show(header=true)
        compose.runOnIdle {f.largeBar=false};step(64)
        compose.onNodeWithTag("small-page-bar").assertExists()
        compose.onNodeWithTag("large-page-bar").assertDoesNotExist()
        dragHeader("page-subtitle",leftTitle=true);release();rest()
        assertEquals(1,f.opens);assertTrue(f.state.destination is Destination.CardCamera)
        assertEquals(0f,top(f.cameraIdentity),.2f)
    }
    @Test fun registeredHeadingDoesNotEnableCameraPullOnAnEditorOrWithTwoPointers() {
        val f=show(Destination.CardEditor("existing"),header=true)
        dragHeader("page-title",leftTitle=true);release();step(32)
        assertEquals(0,f.opens);assertEquals(Destination.CardEditor("existing"),f.state.destination)
        compose.runOnIdle {f.state=f.state.copy(destination=Destination.Cards)};step(700)
        val title=compose.onNodeWithTag("page-title").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("camera-pull-root").performTouchInput {
            val x=width*(title.left.value+8f)/360f;val y=height*(title.top.value+title.bottom.value)/1280f
            down(0,Offset(x,y));down(1,Offset(x+16f,y))
            moveTo(0,Offset(x,y+height*.45f));moveTo(1,Offset(x+16f,y+height*.45f));up(0);up(1)
        };step(64)
        assertEquals(0,f.opens);assertEquals(Destination.Cards,f.state.destination)
    }

    @Test fun shortPullOpensAtSlopThenCancelsWithTheSameRetainedPoseAndRestoresTheBrowser() {
        val f=show();beginDrag(.32f,.44f)
        assertEquals(1,f.opens);assertTrue(f.state.destination is Destination.CardCamera)
        val before=top(f.cameraIdentity);assertTrue(before in -630f..-300f)
        assertEquals(0f,layer(f.cameraIdentity).getUnclippedBoundsInRoot().left.value,.2f);assertTrue(top(f.parentIdentity)>0f)
        release();assertEquals(Destination.Cards,f.state.destination)
        assertTrue("Cancel starts at the dragged pose",abs(top(f.cameraIdentity)-before)<2f)
        rest();layer(f.cameraIdentity).assertDoesNotExist();assertEquals(0f,top(f.parentIdentity),.2f);assertEquals(1,f.backs)
    }
    @Test fun longPullCompletesAndSingleFingerCanCancelThenCompleteAnUpwardExit() {
        val f=show();beginDrag(.28f,.78f);release();step(600)
        assertTrue(f.state.destination is Destination.CardCamera);assertEquals(0f,top(f.cameraIdentity),.2f)
        assertTrue(top(f.parentIdentity)>40f);assertEquals(0,f.backs)
        beginDrag(.4f,.3f);release();step(600)
        assertTrue(f.state.destination is Destination.CardCamera);assertEquals(0f,top(f.cameraIdentity),.2f);assertEquals(0,f.backs)
        beginDrag(.56f,.18f);val before=top(f.cameraIdentity);release()
        assertEquals(Destination.Cards,f.state.destination);assertTrue(abs(top(f.cameraIdentity)-before)<2f)
        rest();layer(f.cameraIdentity).assertDoesNotExist();assertEquals(0f,top(f.parentIdentity),.2f);assertEquals(1,f.backs)
    }
    @Test fun reversingTheSamePointerAfterOpeningTheCameraCanReturnWithoutAnotherNavigation() {
        val f=show();beginDrag(.28f,.78f)
        assertEquals(1,f.opens)
        compose.onNodeWithTag("camera-pull-root").performTouchInput {moveTo(Offset(width*.5f,height*.31f),delayMillis=160)}
        step(16);release();rest()
        assertEquals(Destination.Cards,f.state.destination);assertEquals(1,f.opens);assertEquals(1,f.backs)
        layer(f.cameraIdentity).assertDoesNotExist()
    }
    @Test fun releaseCannotPopAnUnrelatedPageThatAppearedWhileThePointerWasHeld() {
        val f=show();beginDrag(.3f,.43f)
        compose.runOnIdle {f.state=f.state.copy(destination=Destination.Settings,navigationStack=f.state.navigationStack+f.state.destination)}
        release();rest()
        assertEquals(Destination.Settings,f.state.destination);assertEquals(0,f.backs)
        assertEquals(1,f.opens)
    }
    @Test fun aNewUpwardGestureCanReverseAnUnfinishedEntranceFromItsCurrentPose() {
        val f=show();beginDrag(.3f,.72f);release();step(32)
        val before=top(f.cameraIdentity);assertTrue(before<-.1f)
        beginDrag(.45f,.32f)
        val after=top(f.cameraIdentity)
        assertTrue("Reversal continues from its pressed pose: $before -> $after",after<before-20f)
        release();rest()
        assertEquals(Destination.Cards,f.state.destination);assertEquals(1,f.opens);assertEquals(1,f.backs)
        assertEquals(0f,top(f.parentIdentity),.2f)
    }
    @Test fun theSideBrowserRemainsItsParentAfterACameraPullAndNormalUpwardReturn() {
        val f=show(side=true);beginDrag(.3f,.8f);release();step(600)
        assertTrue(f.state.destination is Destination.CardCamera);assertEquals(SidePage.CARDS,f.state.sidePage)
        beginDrag(.56f,.15f);release();rest()
        assertEquals(Destination.Home,f.state.destination);assertEquals(SidePage.CARDS,f.state.sidePage)
        assertEquals(0f,top("1/side/cards"),.2f);layer(f.cameraIdentity).assertDoesNotExist()
    }
    @Test fun twoFingersBottomControlsAndConsumedNativeSliderDragsCannotOpenACamera() {
        val f=show()
        compose.onNodeWithTag("camera-pull-root").performTouchInput {
            down(0,Offset(width*.45f,height*.3f));down(1,Offset(width*.55f,height*.3f))
            moveTo(0,Offset(width*.45f,height*.7f));moveTo(1,Offset(width*.55f,height*.7f));up(0);up(1)
        };step(32);assertEquals(0,f.opens)
        beginDrag(.72f,.98f);release();assertEquals(0,f.opens)
        beginDrag(.3f,.8f,x=.9f);release();assertEquals(0,f.opens)
        compose.runOnIdle {f.slider=true};step(32);compose.onNodeWithTag("fixture-native-slider").assertExists()
        beginDrag(.3f,.8f);release();rest()
        assertTrue(f.sliderDistance>0f);assertEquals(0,f.opens);assertEquals(Destination.Cards,f.state.destination)
    }
    @Test fun aConsumedSliderUpwardDragInANormallyEnteredCameraChangesTheSliderWithoutReturning() {
        val f=show();beginDrag(.3f,.8f);release();step(600)
        assertTrue(f.state.destination is Destination.CardCamera)
        compose.runOnIdle {f.slider=true};step(32)
        compose.onNodeWithTag("fixture-native-slider").assertExists()
        beginDrag(.58f,.15f);release();rest()
        assertTrue("The slider receives its first drag segment",f.sliderDistance< -20f)
        assertEquals(0,f.backs);assertTrue(f.state.destination is Destination.CardCamera)
    }
    @Test fun anExcludedCentralExposureControlWinsOverARegisteredNativeViewfinder() {
        val f=show();beginDrag(.3f,.8f);release();step(600)
        compose.runOnIdle {f.nativeViewfinder=true;f.exposureControl=true};step(32)
        beginDrag(.55f,.35f);release();step(64)
        assertTrue("Central exposure receives the initial movement",f.sliderDistance< -20f)
        assertEquals(0,f.backs);assertTrue(f.state.destination is Destination.CardCamera)
        compose.runOnIdle {f.exposureControl=false};step(32)
        beginDrag(.42f,.05f);release();rest()
        assertEquals(1,f.backs);assertEquals(Destination.Cards,f.state.destination)
    }
    @Test fun cameraAndPrinterGuardsAggregateAndDoNotLeaveALockAfterBothSourcesClear() {
        val f=show();compose.runOnIdle {f.cameraGuard=true;f.printerGuard=true};step(32)
        compose.runOnIdle {assertTrue(f.pull.blocked)}
        beginDrag(.3f,.8f);release();assertEquals(0,f.opens)
        compose.runOnIdle {f.cameraGuard=false};step(32);beginDrag(.3f,.8f);release();assertEquals(0,f.opens)
        compose.runOnIdle {f.printerGuard=false};step(32);beginDrag(.3f,.8f);release();step(600)
        compose.runOnIdle {assertFalse(f.pull.blocked)}
        assertEquals(1,f.opens)
        compose.runOnIdle {f.cameraGuard=true};step(32);beginDrag(.58f,.15f);release();assertEquals(0,f.backs)
        compose.runOnIdle {f.cameraGuard=false;f.printerGuard=true};step(32);beginDrag(.58f,.15f);release();assertEquals(0,f.backs)
        compose.runOnIdle {f.printerGuard=false};step(32);beginDrag(.58f,.15f);release();rest();assertEquals(1,f.backs)
    }
    @Test fun programmaticCameraEntryAndPredictiveReturnUseVerticalMotionFromEitherSystemEdge() {
        val f=show(Destination.CardEditor("existing"))
        compose.runOnIdle {f.open()};step(16);step(96)
        assertTrue(top(f.cameraIdentity)<-1f);assertEquals(0f,layer(f.cameraIdentity).getUnclippedBoundsInRoot().left.value,.2f)
        step(600)
        val dispatcher=compose.activity.onBackPressedDispatcher
        fun predict(edge:Int) {
            compose.runOnIdle {
                dispatcher.dispatchOnBackStarted(BackEventCompat(0f,100f,0f,edge))
                dispatcher.dispatchOnBackProgressed(BackEventCompat(100f,100f,.55f,edge))
            };step(64)
        }
        predict(BackEventCompat.EDGE_LEFT);val first=top(f.cameraIdentity);val parent=top(f.parentIdentity)
        assertTrue(first<-100f);assertTrue(parent in 1f..40f)
        compose.runOnIdle {dispatcher.dispatchOnBackCancelled()};step(800)
        assertEquals(0f,top(f.cameraIdentity),.2f)
        predict(BackEventCompat.EDGE_RIGHT);assertEquals(first,top(f.cameraIdentity),.2f)
        val before=top(f.cameraIdentity);val parentBefore=top(f.parentIdentity)
        compose.runOnIdle {dispatcher.onBackPressed()}
        assertTrue("Predictive commit preserves camera pose",abs(top(f.cameraIdentity)-before)<2f)
        assertTrue("Predictive commit preserves parent pose",abs(top(f.parentIdentity)-parentBefore)<2f)
        step(96);assertTrue(top(f.cameraIdentity)<before-20f);rest()
        assertEquals(Destination.CardEditor("existing"),f.state.destination);assertEquals(0f,top(f.parentIdentity),.2f)
    }
}
