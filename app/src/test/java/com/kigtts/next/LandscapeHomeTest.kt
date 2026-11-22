package com.kigtts.next

import android.app.Application
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.state.*
import com.kigtts.next.theme.ThemeMode
import com.kigtts.next.ui.KigttsApp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w852dp-h393dp-land-xxhdpi")
class LandscapeHomeTest {
    @Test fun landscapeCameraButtonUsesHoldForKigttsWhenSystemCameraIsDefault() {
        val model = show()
        val preferences = compose.activity.getSharedPreferences("polaroid_camera", 0)
        compose.runOnIdle {
            model.closePanel()
            preferences.edit().putBoolean("use-system-camera", true).apply()
        }
        val camera = compose.onNodeWithTag("dock-start")
        compose.onNodeWithTag("landscape-quick-panel").assertIsDisplayed()
        camera.assertContentDescriptionEquals("相机").performSemanticsAction(SemanticsActions.OnLongClick)
        compose.waitForIdle()
        assertEquals(false, (model.state.value.destination as? Destination.CardCamera)?.systemCamera)
    }
    @Test fun draggingToCollapsedRestoresTheLargePillBeforeTheFingerIsReleasedEvenWithPluginSlots() {
        val model=show();val density=compose.activity.resources.displayMetrics.density
        compose.runOnIdle {
            model.panelColumns(2);model.openPanel(HomePanel.QUICK_TEXT)
            repeat(2) { model.invoke("debug.ui","QUICK_PANEL_START/add");model.invoke("debug.ui","QUICK_PANEL_END/add") }
        }
        compose.waitForIdle();compose.mainClock.autoAdvance=false
        compose.onNodeWithTag("sheet-handle").performTouchInput { down(center);moveBy(Offset(280f*density,0f),delayMillis=500) }
        repeat(40) { compose.mainClock.advanceTimeByFrame();compose.waitForIdle() }
        assertEquals(HomePanel.QUICK_TEXT,model.state.value.panel)
        assertEquals(64f,compose.onNodeWithTag("landscape-quick-panel").widthDp(),.5f)
        val pill=compose.onNodeWithTag("dock-main").getUnclippedBoundsInRoot()
        assertEquals(48f,(pill.right-pill.left).value,.5f);assertEquals(160f,(pill.bottom-pill.top).value,.5f)
        for(tag in listOf("dock-start","dock-end"))assertEquals(48f,compose.onNodeWithTag(tag).widthDp(),.5f)
        compose.onRoot().captureRoboImage("build/previews/landscape-dragged-closed-before-release.png")
        compose.onNodeWithTag("sheet-handle").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1200);compose.mainClock.autoAdvance=true
        assertNull(model.state.value.panel);assertEquals(2,model.state.value.panelColumns)
    }
    @Test fun landscapeDocksUseCircularSideButtonsLargerPillAndTheRequestedVerticalOrder() {
        val model=show()
        fun bounds(tag:String)=compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        val menu=compose.onNodeWithContentDescription("打开设置").getUnclippedBoundsInRoot()
        val speaker=bounds("auto-playback")
        assertTrue(menu.bottom<speaker.top)
        compose.onNodeWithTag("dock-start").assertContentDescriptionEquals("拍立得")
        compose.onNodeWithTag("dock-end").assertContentDescriptionEquals("画板")
        assertTrue(bounds("dock-start").bottom<bounds("dock-end").top)
        for(tag in listOf("dock-start","dock-end")) {
            val box=bounds(tag);assertEquals(48f,(box.right-box.left).value,.5f);assertEquals(48f,(box.bottom-box.top).value,.5f)
        }
        val main=bounds("dock-main");assertEquals(48f,(main.right-main.left).value,.5f);assertEquals(160f,(main.bottom-main.top).value,.5f)
        compose.onRoot().captureRoboImage("build/previews/landscape-collapsed-circles.png")
        compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT) }
        for(tag in listOf("dock-start","dock-end")) {
            val box=bounds(tag);assertEquals(32f,(box.right-box.left).value,.5f);assertEquals(32f,(box.bottom-box.top).value,.5f)
        }
        compose.onRoot().captureRoboImage("build/previews/landscape-expanded-circles.png")
    }
    @Test fun landscapeSettingsOpenDirectlyAndSketchEntersAndReturnsRightRegardlessOfBackGestureEdge() {
        val model=show()
        compose.onNodeWithContentDescription("打开设置").performClick()
        compose.runOnIdle { assertEquals(Destination.Settings, model.state.value.destination) }
        compose.onNodeWithTag("app-drawer").assertDoesNotExist()
        val settings=compose.onNodeWithTag("navigation-secondary").getUnclippedBoundsInRoot()
        compose.mainClock.autoAdvance=false
        fun step(ms:Long) { compose.mainClock.advanceTimeBy(ms);compose.waitForIdle() }
        fun gesture(edge:Int) { compose.runOnIdle {
            compose.activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f,100f,0f,edge))
            compose.activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(100f,100f,.6f,edge))
        };step(64) }
        gesture(BackEventCompat.EDGE_LEFT)
        assertTrue(compose.onNodeWithTag("navigation-secondary").getUnclippedBoundsInRoot().left.value>0f)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled() };step(600)
        assertEquals(settings,compose.onNodeWithTag("navigation-secondary").getUnclippedBoundsInRoot())
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() };step(1200)
        compose.runOnIdle { model.openSidePage(SidePage.SKETCH) };step(96)
        val page=compose.onNodeWithTag("page-layer/1/side/sketch")
        assertTrue(page.getUnclippedBoundsInRoot().left.value>0f);step(800)
        for(edge in listOf(BackEventCompat.EDGE_LEFT,BackEventCompat.EDGE_RIGHT)) {
            gesture(edge);assertTrue(page.getUnclippedBoundsInRoot().left.value>0f)
            compose.runOnIdle { compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled() };step(800)
        }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() };step(96)
        assertTrue(page.getUnclippedBoundsInRoot().left.value>0f);step(1000)
        page.assertDoesNotExist();compose.mainClock.autoAdvance=true
    }
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun show(): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("appearance", "ui-debug", "home-panel", "voice-input", "quick-phrases").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        return testAppModel(app).also { compose.setContent { KigttsApp(it) } }
    }
    @Test fun landscapeOrdersPanesAndChangesWholeColumnsWithoutChangingPortraitHeight() {
        val model = show()
        compose.onNodeWithTag("landscape-app-rail").assertIsDisplayed()
        compose.onNodeWithTag("captions").assertDoesNotExist()
        compose.runOnIdle { model.toggleListening(); model.openPanel(HomePanel.QUICK_TEXT) }
        val left = compose.onNodeWithTag("listening-card").getUnclippedBoundsInRoot()
        val main = compose.onNodeWithTag("editor-area").getUnclippedBoundsInRoot()
        val right = compose.onNodeWithTag("landscape-quick-panel").getUnclippedBoundsInRoot()
        assertTrue(left.right < main.left); assertTrue(main.right < right.left)
        assertEquals(left.top.value, main.top.value, .5f)
        compose.onRoot().captureRoboImage("build/previews/landscape-one-column-light.png")
        val items = model.phrases.value.filter { it.category == "常用" }.take(2)
        val first = compose.onNodeWithTag("quick-phrase-" + items[0].id).getUnclippedBoundsInRoot()
        val second = compose.onNodeWithTag("quick-phrase-" + items[1].id).getUnclippedBoundsInRoot()
        assertTrue(second.top > first.top)
        compose.runOnIdle { model.panelColumns(2); model.theme(model.state.value.theme.copy(mode = ThemeMode.DARK)) }
        val secondAfter = compose.onNodeWithTag("quick-phrase-" + items[1].id).getUnclippedBoundsInRoot()
        val firstAfter = compose.onNodeWithTag("quick-phrase-" + items[0].id).getUnclippedBoundsInRoot()
        assertEquals(firstAfter.top.value, secondAfter.top.value, .5f)
        assertTrue(secondAfter.left > firstAfter.left)
        assertEquals(184f, model.state.value.lastExpandedPanelHeight)
        assertTrue(compose.onNodeWithTag("editor-area").widthDp() >= 220f)
        compose.onRoot().captureRoboImage("build/previews/landscape-two-columns-dark.png")
    }
    @Test fun collapseReopenAndRestartRestoreColumnsAndScrollingDoesNotResize() {
        val model = show()
        compose.runOnIdle {
            repeat(25) { model.addPhrase("测试条目$it", "常用") }
            model.panelColumns(3); model.openPanel(HomePanel.QUICK_TEXT)
        }
        compose.onNodeWithTag("phrases-vertical").performTouchInput { swipeUp() }
        assertTrue(compose.onNodeWithTag("phrases-vertical").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
        assertEquals(3, model.state.value.panelColumns)
        compose.onNodeWithContentDescription("收起快捷文本").performClick()
        compose.onNodeWithTag("dock-main").performClick()
        assertEquals(3, model.state.value.panelColumns)
        assertEquals(3, testAppModel(ApplicationProvider.getApplicationContext()).state.value.panelColumns)
        compose.onNodeWithTag("phrases-horizontal").assertDoesNotExist()
        compose.onRoot().captureRoboImage("build/previews/landscape-three-columns.png")
    }
    @Test fun dragFromCollapsedSurvivesPillMorphAndRestoresLastColumnCount() {
        val model = show()
        compose.runOnIdle { model.panelColumns(2) }
        val density = compose.activity.resources.displayMetrics.density
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("sheet-handle").performTouchInput { down(center); moveBy(Offset(-25f * density, 0f)) }
        repeat(8) { step ->
            compose.mainClock.advanceTimeBy(24); compose.waitForIdle()
            compose.onNodeWithTag("sheet-handle").performTouchInput { moveBy(Offset(-16f * density, 0f)) }
            if (step == 3) compose.runOnIdle {
                repeat(3) { model.invoke("debug.ui", "QUICK_PANEL_START/add"); model.invoke("debug.ui", "QUICK_PANEL_END/add") }
            }
        }
        assertEquals(HomePanel.QUICK_TEXT, model.state.value.panel)
        assertEquals(2, model.state.value.panelColumns)
        val during = compose.onNodeWithTag("landscape-quick-panel").widthDp()
        assertTrue(during > 180f && during < 316f)
        compose.onNodeWithTag("sheet-handle").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1500); compose.mainClock.autoAdvance = true
        assertEquals(316f, compose.onNodeWithTag("landscape-quick-panel").widthDp(), 1f)
        assertEquals(2, model.state.value.panelColumns)
        compose.onNodeWithTag("QUICK_PANEL_START-more").assertIsDisplayed()
        compose.onNodeWithTag("QUICK_PANEL_END-more").assertIsDisplayed()
    }
    @Test fun listeningConstraintsTemporarilyLimitColumnsWithoutOverwritingThePreference() {
        val model = show()
        compose.runOnIdle { model.panelColumns(3); model.openPanel(HomePanel.QUICK_TEXT); model.toggleListening() }
        compose.onNodeWithTag("landscape-quick-panel").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "2列快捷文本"))
        assertEquals(3, model.state.value.panelColumns)
        compose.runOnIdle { model.closePanel() }
        val density = compose.activity.resources.displayMetrics.density
        compose.onNodeWithTag("sheet-handle").performTouchInput {
            down(center); moveBy(Offset(-180f * density, 0f)); up()
        }
        compose.onNodeWithTag("landscape-quick-panel").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "2列快捷文本"))
        assertEquals("Dragging open restores rather than replacing a constrained preference", 3, model.state.value.panelColumns)
        compose.runOnIdle { model.closeListeningPanel() }
        compose.onNodeWithTag("landscape-quick-panel").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "3列快捷文本"))
        assertEquals(3, model.state.value.panelColumns)
    }
    @Test fun inputAndQuickEditingUseWholeViewportAndReturnToTheSameLayout() {
        val model = show()
        compose.runOnIdle { model.toggleListening(); model.panelColumns(2); model.openPanel(HomePanel.QUICK_TEXT) }
        compose.runOnIdle { model.editing(true) }
        compose.onNodeWithTag("landscape-app-rail").assertDoesNotExist()
        compose.onNodeWithTag("captions").assertDoesNotExist()
        compose.onNodeWithTag("landscape-quick-panel").assertDoesNotExist()
        val whole = compose.onNodeWithTag("landscape-home").getUnclippedBoundsInRoot()
        val editor = compose.onNodeWithTag("editor-area").getUnclippedBoundsInRoot()
        assertEquals((whole.right - whole.left).value, (editor.right - editor.left).value, 1f)
        assertEquals((whole.bottom - whole.top).value, (editor.bottom - editor.top).value, 1f)
        compose.onRoot().captureRoboImage("build/previews/landscape-input-fullscreen.png")
        compose.runOnIdle { model.editing(false); model.editQuickTexts() }
        compose.onNodeWithTag("quick-text-editor").assertIsDisplayed()
        compose.onNodeWithTag("landscape-app-rail").assertDoesNotExist()
        val items = model.phrases.value.filter { it.category == "常用" }.take(3)
        val first = compose.onNodeWithTag("edit-phrase-" + items[0].id).getUnclippedBoundsInRoot()
        val third = compose.onNodeWithTag("edit-phrase-" + items[2].id).getUnclippedBoundsInRoot()
        assertEquals(first.top.value, third.top.value, .5f)
        compose.onRoot().captureRoboImage("build/previews/landscape-fullscreen-edit.png")
        compose.onNodeWithContentDescription("完成编辑").performClick()
        assertEquals(2, model.state.value.panelColumns)
        compose.onNodeWithTag("landscape-app-rail").assertIsDisplayed()
        compose.onNodeWithTag("captions").assertIsDisplayed()
    }
    @Test fun listeningWidthHandleResizesHorizontallyAndFontControlsRemainInsideTheCard() {
        val model = show()
        compose.runOnIdle { model.toggleListening(); model.openPanel(HomePanel.QUICK_TEXT); model.panelColumns(2) }
        val original = compose.onNodeWithTag("listening-card").widthDp()
        val density = compose.activity.resources.displayMetrics.density
        compose.onNodeWithTag("top-panel-resize").performTouchInput { down(center); moveBy(Offset(28f * density, 0f)); up() }
        val enlarged = compose.onNodeWithTag("listening-card").widthDp()
        assertTrue(enlarged > original)
        assertEquals(128f, model.state.value.captionHeight)
        compose.onNodeWithContentDescription("调整聆听字号").performClick()
        val card = compose.onNodeWithTag("listening-card").getUnclippedBoundsInRoot()
        val controls = compose.onNodeWithTag("listening-controls").getUnclippedBoundsInRoot()
        assertTrue(controls.left >= card.left && controls.right <= card.right)
    }
}

private fun SemanticsNodeInteraction.widthDp(): Float = getUnclippedBoundsInRoot().let { (it.right - it.left).value }
