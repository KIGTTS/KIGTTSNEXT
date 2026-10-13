package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextRange
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.state.*
import com.kigtts.next.theme.KigttsTheme
import com.kigtts.next.theme.ThemeSettings
import com.kigtts.next.ui.KigttsApp
import com.kigtts.next.ui.navigation.PageScaffold
import com.kigtts.next.ui.plugins.PluginSourceEditorScreen
import com.kigtts.plugin.api.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED) @Config(sdk=[35],qualifiers="zh-rCN-w393dp-h852dp-xxhdpi")
class PluginSourceRestorationUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val conflict="文件已在其它位置更新，请先保存或放弃当前修改"
    @Test fun recreationKeepsUnsavedSourceAndSelectionWithoutInventingARemoteConflict() {
        val block=PageBlock.SourceEditor("project/main.lua","return 0","save")
        val requests=mutableListOf<String>();var backs=0
        val restoration=StateRestorationTester(compose)
        restoration.setContent { KigttsTheme(ThemeSettings()) { PageScaffold("源码",{backs++},largeBar=false) {
            PluginSourceEditorScreen(block,true) {_,inputs->requests+=inputs.getValue("value")}
        } } }
        val draft="local value = 42\nreturn value";val selection=TextRange(6,19)
        compose.onNodeWithTag("content-editor-text").performTextReplacement(draft)
        compose.onNodeWithTag("content-editor-text").performTextInputSelection(selection)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("content-editor-text").assertTextEquals(draft)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange,selection))
        compose.onNodeWithText(conflict).assertDoesNotExist()
        compose.onNodeWithTag("page-back").performClick()
        compose.onNodeWithTag("save-changes-save").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("save-changes-keep").performClick()
        assertTrue(requests.isEmpty());assertEquals(0,backs)
        compose.onNodeWithTag("content-editor-text").assertTextEquals(draft)
    }
    @Test fun aRealExternalUpdateAfterRecreationRetainsTheDraftAndItsOriginalDirtyBaseline() {
        var block by mutableStateOf(PageBlock.SourceEditor("project/main.lua","return 0","save"))
        val requests=mutableListOf<String>();var backs=0
        val restoration=StateRestorationTester(compose)
        restoration.setContent { KigttsTheme(ThemeSettings()) { PageScaffold("源码",{backs++},largeBar=false) {
            PluginSourceEditorScreen(block,true) {_,inputs->requests+=inputs.getValue("value")}
        } } }
        val draft="return '尚未保存'"
        compose.onNodeWithTag("content-editor-text").performTextReplacement(draft)
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle {block=block.copy(text="return '外部修改'")}
        compose.onNodeWithText(conflict).assertIsDisplayed()
        compose.onNodeWithTag("content-editor-text").assertTextEquals(draft)
        compose.onNodeWithTag("page-back").performClick()
        compose.onNodeWithTag("save-changes-save").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("save-changes-keep").performClick()
        assertTrue(requests.isEmpty());assertEquals(0,backs)
        assertEquals("return '外部修改'",block.text)
        compose.onNodeWithTag("content-editor-text").assertTextEquals(draft)
    }
    @Test fun saveAcknowledgedUnderARealChildSceneAndRecreationReturnsOnlyAfterTheEditorIsForeground() {
        val gate=CompletableDeferred<Unit>();val requests=CopyOnWriteArrayList<String>()
        var stored="return 0"
        val plugin=object:KigPlugin {
            override val descriptor=PluginDescriptor("test.source-return","源码","test")
            override fun page(pageId:String,context:PluginContext)=PluginPage("source","源码",
                listOf(PageBlock.SourceEditor("project/main.lua",stored,"save")))
            override suspend fun execute(actionId:String,context:PluginContext):PluginOutcome {
                val draft=context.inputs.getValue("value");requests+=draft
                gate.await();stored=draft;return PluginOutcome.RefreshUi
            }
        }
        val model=AppViewModel(ApplicationProvider.getApplicationContext<Application>(),listOf(plugin))
        val source=Destination.PluginPage(plugin.descriptor.id,"source");model.navigate(source)
        val restoration=StateRestorationTester(compose)
        restoration.setContent {KigttsApp(model)}
        compose.onNodeWithTag("content-editor-text").performTextReplacement("return '一次保存'")
        compose.onNodeWithTag("page-back").performClick()
        compose.onNodeWithTag("save-changes-save").performClick()
        compose.waitUntil(8_000) {requests.size==1&&plugin.descriptor.id in model.state.value.busyPlugins}
        compose.runOnIdle {model.navigate(Destination.Settings)}
        compose.waitForIdle()
        compose.runOnIdle {gate.complete(Unit)}
        compose.waitUntil(8_000) {plugin.descriptor.id !in model.state.value.busyPlugins}
        assertEquals(Destination.Settings,model.state.value.destination)
        assertEquals(listOf(Destination.Home,source),model.state.value.navigationStack)
        compose.onNode(isDialog()).assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        assertEquals(Destination.Settings,model.state.value.destination)
        compose.runOnIdle {model.navigateBack()}
        compose.waitForIdle()
        assertEquals(Destination.Home,model.state.value.destination)
        assertTrue(model.state.value.navigationStack.isEmpty())
        assertEquals(listOf("return '一次保存'"),requests.toList())
        assertEquals("return '一次保存'",stored)
        compose.onNode(isDialog()).assertDoesNotExist()
        // Re-entering the source uses the committed baseline and cannot repeat the old return.
        compose.runOnIdle {model.navigate(source)}
        compose.onNodeWithTag("content-editor-text").assertTextEquals(stored)
        compose.onNodeWithText(conflict).assertDoesNotExist()
        compose.onNodeWithTag("page-back").performClick()
        assertEquals(Destination.Home,model.state.value.destination)
        assertEquals(1,requests.size)
    }
    @Test fun anActualRetirementDoesNotRestoreAnAcceptedReturnWhenTheSameSourceIsOpenedAgain() {
        val gate=CompletableDeferred<Unit>();val requests=CopyOnWriteArrayList<String>();var stored="return 0"
        val plugin=object:KigPlugin {
            override val descriptor=PluginDescriptor("test.source-retire","源码","test")
            override fun page(pageId:String,context:PluginContext)=PluginPage("source","源码",
                listOf(PageBlock.SourceEditor("project/main.lua",stored,"save")))
            override suspend fun execute(actionId:String,context:PluginContext):PluginOutcome {
                val draft=context.inputs.getValue("value");requests+=draft;gate.await();stored=draft
                return PluginOutcome.RefreshUi
            }
        }
        val model=AppViewModel(ApplicationProvider.getApplicationContext<Application>(),listOf(plugin))
        val source=Destination.PluginPage(plugin.descriptor.id,"source");model.navigate(source)
        compose.setContent {KigttsApp(model)}
        compose.onNodeWithTag("content-editor-text").performTextReplacement("return '已保存'")
        compose.onNodeWithTag("page-back").performClick()
        compose.onNodeWithTag("save-changes-save").performClick()
        compose.waitUntil(8_000) {requests.size==1&&plugin.descriptor.id in model.state.value.busyPlugins}
        compose.runOnIdle {model.navigate(Destination.Settings)};compose.waitForIdle()
        compose.runOnIdle {gate.complete(Unit)}
        compose.waitUntil(8_000) {plugin.descriptor.id !in model.state.value.busyPlugins};compose.waitForIdle()
        assertEquals(Destination.Settings,model.state.value.destination)
        // The owner is really popped, not merely covered. Its accepted return belongs to that visit.
        compose.runOnIdle {model.navigate(Destination.Home)};compose.waitForIdle()
        assertTrue(model.state.value.navigationStack.isEmpty());compose.onNodeWithTag("content-editor-text").assertDoesNotExist()
        compose.runOnIdle {model.navigate(source)};compose.waitForIdle()
        assertEquals(source,model.state.value.destination)
        compose.onNodeWithTag("content-editor-text").assertTextEquals("return '已保存'")
        compose.onNodeWithTag("source-save/project/main.lua").assertIsNotEnabled()
        compose.onNode(isDialog()).assertDoesNotExist();assertEquals(1,requests.size)
        compose.onNodeWithTag("page-back").performClick();compose.waitForIdle()
        assertEquals(Destination.Home,model.state.value.destination);assertEquals(1,requests.size)
    }
}
