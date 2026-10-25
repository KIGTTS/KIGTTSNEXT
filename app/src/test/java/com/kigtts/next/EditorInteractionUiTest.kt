package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.data.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import com.kigtts.next.text.*
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.navigation.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk=[35],qualifiers="zh-rCN-w393dp-h852dp-xxhdpi")
class EditorInteractionUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun openMenuUpdatesItsActionsWhenSelectionChangesWithoutRecreatingTheSession() {
        val expanded=mutableStateOf(false);var calls=0;lateinit var provider:IconTextMenuProvider
        val data=object:TextContextMenuDataProvider {
            override fun position(destinationCoordinates:LayoutCoordinates)=Offset(24f,120f)
            override fun contentBounds(destinationCoordinates:LayoutCoordinates)=Rect(24f,120f,240f,180f)
            override fun data()=TextContextMenuData(listOf(TextContextMenuItem(TextContextMenuKeys.SelectAllKey,"全选") { expanded.value=true })+
                if(expanded.value)listOf(TextContextMenuItem(TextFormat.BOLD,"粗体") { calls++ })else emptyList())
        }
        compose.setContent { MaterialTheme { ProvideIconTextContextMenu {
            val current=LocalTextContextMenuToolbarProvider.current as IconTextMenuProvider
            SideEffect { provider=current };LaunchedEffect(Unit) { current.showTextContextMenu(data) }
            Box(Modifier.fillMaxSize())
        } } }
        compose.onNodeWithTag("text-menu-bold").assertDoesNotExist()
        lateinit var original:IconTextMenuSession
        compose.runOnIdle { original=provider.session!! }
        compose.onNodeWithTag("text-menu-select-all").performClick()
        compose.onNodeWithTag("text-menu-bold").assertIsDisplayed().performClick()
        compose.runOnIdle { assertSame(original,provider.session);assertEquals(1,calls) }
    }
    @Test @Config(sdk=[27]) fun selectingNativeTextOffersIconActionsAndFormattingKeepsFocus() {
        val context=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager=org.robolectric.Shadows.shadowOf(context.packageManager)
        val process=android.content.Intent(android.content.Intent.ACTION_PROCESS_TEXT).setType("text/plain")
        listOf("朗读","翻译").forEachIndexed { index,label ->
            manager.addResolveInfoForIntent(process,android.content.pm.ResolveInfo().apply {
                nonLocalizedLabel=label
                activityInfo=android.content.pm.ActivityInfo().apply { packageName=context.packageName;name="Process$index";exported=true }
            })
        }
        val editor=RichEditorState("hello friends",emptyList())
        // API27 avoids Robolectric's unsupported platform Magnifier Surface;
        // the public Foundation provider and real long-press selection still run.
        compose.setContent { MaterialTheme { ProvideIconTextContextMenu {
            val menu=Modifier.richTextMenu(true,{editor.value.selection},editor.formats,editor::format)
            OutlinedTextField(editor.value,editor::change,Modifier.padding(24.dp).fillMaxWidth().testTag("field").then(menu)
                .textMenuAnchor { editor.change(editor.value.copy(selection=TextRange(editor.value.selection.end))) },
                visualTransformation=remember(editor.formats) { RichTextVisualTransformation(editor.formats) })
        } } }
        fun open() {
            compose.onNodeWithTag("field").performTouchInput { longClick(Offset(70f,height/2f)) }
            compose.onNodeWithTag("text-icon-menu").assertIsDisplayed()
            compose.onNodeWithTag("text-menu-copy").assertIsDisplayed()
        }
        open()
        compose.onNodeWithContentDescription("朗读").assertDoesNotExist()
        compose.onNodeWithContentDescription("翻译").assertDoesNotExist()
        listOf("bold" to TextFormat.BOLD,"italic" to TextFormat.ITALIC,"underline" to TextFormat.UNDERLINE).forEach { (tag,kind) ->
            compose.onNodeWithTag("text-menu-$tag").performClick()
            compose.onNodeWithTag("text-icon-menu").assertIsDisplayed()
            compose.onNodeWithTag("field").assertIsFocused()
            compose.runOnIdle { assertTrue(editor.formats.any { it.kind==kind }) }
        }
        compose.onNodeWithTag("text-menu-size").performClick()
        listOf("小","正常","大").forEach { compose.onNodeWithText(it,useUnmergedTree=true).assertExists() }
        compose.onNodeWithText("大",useUnmergedTree=true).performClick()
        compose.runOnIdle { assertTrue(editor.formats.any { it.kind==TextFormat.SIZE&&it.value==125L }) }
    }
    @Test @Config(sdk=[27]) fun titleMenuFollowsItsFieldAndBackDismissesOnceWithoutReopening() {
        val body=mutableStateOf(TextFieldValue("body content"));val title=mutableStateOf(TextFieldValue("thumbnail title"));var back=0
        val shift=mutableStateOf(0)
        lateinit var provider:IconTextMenuProvider;lateinit var root:LayoutCoordinates
        compose.mainClock.autoAdvance=false
        compose.setContent { MaterialTheme {
            BackHandler { back++ }
            ProvideIconTextContextMenu {
                val current=LocalTextContextMenuToolbarProvider.current as IconTextMenuProvider
                SideEffect { provider=current }
                Column(Modifier.padding(24.dp).offset { androidx.compose.ui.unit.IntOffset(0,shift.value) }.onGloballyPositioned { root=it }) {
                OutlinedTextField(body.value,{body.value=it},Modifier.fillMaxWidth().height(200.dp).testTag("body")
                    .textMenuAnchor { body.value=body.value.copy(selection=TextRange(body.value.selection.end)) })
                Spacer(Modifier.height(80.dp))
                OutlinedTextField(title.value,{title.value=it},Modifier.fillMaxWidth().testTag("title")
                    .textMenuAnchor { title.value=title.value.copy(selection=TextRange(title.value.selection.end)) })
            } }
        } }
        compose.onNodeWithTag("title").performTouchInput { longClick(Offset(70f,height/2f)) }
        compose.mainClock.advanceTimeBy(240);compose.waitForIdle()
        compose.runOnIdle {
            val session=provider.session!!
            val field=root.localBoundingBoxOf(session.owner!!.coordinates!!)
            val selected=session.data.contentBounds(root)
            assertTrue("Selection bounds must refer to the focused title",selected.top>=field.top&&selected.top<=field.bottom)
            assertTrue(session.owner!!.focused)
        }
        lateinit var sameSession:IconTextMenuSession;var previousTop=0f
        compose.runOnIdle { sameSession=provider.session!!;previousTop=sameSession.owner!!.windowBounds.top;shift.value=60 }
        compose.mainClock.advanceTimeByFrame();compose.waitForIdle()
        compose.runOnIdle {
            assertSame(sameSession,provider.session)
            assertEquals(previousTop+60,sameSession.owner!!.windowBounds.top,1f)
        }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(48);compose.waitForIdle()
        compose.onNodeWithTag("text-icon-menu").assertExists() // Exit window remains until animation completes.
        compose.mainClock.advanceTimeBy(300);compose.waitForIdle()
        compose.onNodeWithTag("text-icon-menu").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(300);compose.waitForIdle()
        compose.onNodeWithTag("text-icon-menu").assertDoesNotExist()
        compose.onNodeWithTag("title").assertIsFocused()
        compose.runOnIdle { assertTrue(title.value.selection.collapsed);assertEquals(0,back);compose.activity.onBackPressedDispatcher.onBackPressed();assertEquals(1,back) }
        compose.onNodeWithTag("title").performTouchInput { longClick(Offset(70f,height/2f)) }
        compose.mainClock.advanceTimeBy(240);compose.waitForIdle()
        compose.onNodeWithTag("text-icon-menu").assertIsDisplayed() // A fresh selection is never suppressed.
    }
    @Test fun appBarCollapsesOnInputWithoutRecomposingEditorOnEveryAnimationFrame() {
        val focused=mutableStateOf(false);var compositions=0
        compose.mainClock.autoAdvance=false
        compose.setContent { MaterialTheme { PageScaffold("编辑",{}) {
            RegisterPageInput(focused.value)
            SideEffect { compositions++ }
            Box(Modifier.fillMaxSize().testTag("editor"))
        } } }
        compose.waitForIdle()
        fun barHeight()=compose.onNodeWithTag("large-page-bar").getUnclippedBoundsInRoot().let { it.bottom-it.top }
        val before=barHeight()
        compose.runOnIdle { focused.value=true }
        compose.waitForIdle();compose.mainClock.advanceTimeByFrame()
        val started=compositions
        repeat(6) { compose.mainClock.advanceTimeByFrame();compose.waitForIdle() }
        val middle=barHeight()
        compose.mainClock.advanceTimeBy(400);compose.waitForIdle()
        val after=barHeight()
        assertTrue(middle<before);assertTrue(after<=middle)
        assertTrue("Editor must not recompose at animation frame rate: ${compositions-started}",compositions-started<=3)
    }
}
