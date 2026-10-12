package com.kigtts.next

import android.app.Application
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.graphics.Insets
import androidx.core.view.*
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.state.*
import com.kigtts.next.theme.*
import com.kigtts.next.ui.home.PhraseTypeTabs
import com.kigtts.next.ui.media.code.HtmlCodeEditorScreen
import com.kigtts.next.ui.navigation.PageScaffold
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk=[35],qualifiers="zh-rCN-w393dp-h852dp-xxhdpi")
class HtmlCodeEditorUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun shortcutsRemainAboveImeExpandedKeysKeepFocusAndSaveTheSelectedFile() {
        val app=ApplicationProvider.getApplicationContext<Application>();val model=testAppModel(app)
        val resource=model.mediaRepository.html("项目","<p>内容</p>")
        runBlocking { model.content.editorFile(resource.id,"index.html","display") }
        val draft=model.content.editorDraft.value!!
        compose.setContent { KigttsTheme(ThemeSettings()) { PageScaffold("index.html",{}) { HtmlCodeEditorScreen(model,draft,false) } } }
        compose.onNodeWithTag("content-editor-text").performTextReplacement("第一行\n第二行\n第三行")
        compose.onNodeWithTag("code-line-numbers").assertIsDisplayed()
        compose.runOnIdle {
            WindowCompat.setDecorFitsSystemWindows(compose.activity.window,false)
            val density=compose.activity.resources.displayMetrics.density
            val insets=WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.ime(),Insets.of(0,0,0,(300*density).toInt()))
                .setVisible(WindowInsetsCompat.Type.ime(),true).build()
            val content=compose.activity.findViewById<ViewGroup>(android.R.id.content)
            ViewCompat.dispatchApplyWindowInsets(content.getChildAt(0),insets)
        };compose.waitForIdle()
        val bottom=compose.onRoot().getUnclippedBoundsInRoot().bottom
        val bar=compose.onNodeWithTag("code-shortcuts").getUnclippedBoundsInRoot()
        assertEquals((bottom.value-300),bar.bottom.value,2f)
        compose.onNodeWithTag("code-expand").performClick();compose.waitForIdle()
        val expanded=compose.onNodeWithTag("code-shortcuts").getUnclippedBoundsInRoot()
        assertTrue(expanded.bottom-expanded.top>bar.bottom-bar.top)
        compose.onNodeWithTag("code-key/SEMICOLON").performClick();compose.onNodeWithTag("content-editor-text").assertIsFocused()
        compose.onNodeWithTag("code-key/UNDO").performClick()
        compose.onNodeWithTag("content-editor-text").assertTextEquals("第一行\n第二行\n第三行")
        compose.onNodeWithTag("code-key/REDO").performClick();compose.waitForIdle()
        assertTrue(model.content.editorDraft.value!!.value.contains(';'))
        compose.onRoot().captureRoboImage("build/previews/html-code-editor-keyboard.png")
        compose.onNodeWithTag("save-content").performClick()
        compose.waitUntil(5000) { model.content.editorDraft.value==null }
        assertTrue(model.mediaRepository.htmlFiles.read(resource.id,"index.html").contains(';'))
    }
    @Test fun cardTypeTabsUseNativeStackedIconsAndInheritTheSectionColour() {
        var type by mutableStateOf(QuickCardType.MEDIA)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Surface(color=MaterialTheme.colorScheme.surfaceContainer,modifier=Modifier.fillMaxWidth()) { PhraseTypeTabs(type,true) { type=it } }
        } }
        QuickCardType.entries.forEach { option ->
            val icon=compose.onNodeWithTag("phrase-type-icon-"+option.name,useUnmergedTree=true).getUnclippedBoundsInRoot()
            val text=compose.onNodeWithText(option.label,useUnmergedTree=true).getUnclippedBoundsInRoot()
            assertTrue("icon=$icon text=$text",icon.bottom<=text.top)
        }
        compose.onRoot().captureRoboImage("build/previews/phrase-native-stacked-tabs.png")
        compose.onNodeWithTag("phrase-type-PAGE").performClick();assertEquals(QuickCardType.PAGE,type)
    }
}
