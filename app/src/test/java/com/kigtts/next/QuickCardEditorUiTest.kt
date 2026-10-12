package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextRange
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.state.*
import com.kigtts.next.text.TextFormat
import com.kigtts.next.ui.KigttsApp
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class QuickCardEditorUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun show(): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("quick-phrases", "appearance", "home-panel").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        return testAppModel(app).also { model ->
            compose.setContent { KigttsApp(model) }
            compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT); model.editQuickTexts() }
            compose.onNodeWithTag("add-phrase").performClick()
        }
    }
    @Test fun fourTabsShareTheirTextAndRichFormattingIsAlwaysAvailableAndPersistsOnSave() {
        val model = show()
        val tabs=compose.onNodeWithTag("phrase-type-tabs").getUnclippedBoundsInRoot()
        assertTrue(compose.onNodeWithTag("phrase-detail-text").getUnclippedBoundsInRoot().bottom < tabs.top)
        assertTrue(compose.onNodeWithTag("phrase-detail-title").getUnclippedBoundsInRoot().bottom < tabs.top)
        QuickCardType.entries.forEach { compose.onNodeWithTag("phrase-type-" + it.name).assertExists()
            compose.onNodeWithTag("phrase-type-icon-"+it.name,useUnmergedTree=true).assertExists() }
        compose.onNodeWithTag("phrase-type-TEXT").assertTextContains("无")
        compose.onNodeWithTag("phrase-rich-text").assertDoesNotExist()
        compose.onNodeWithTag("phrase-format-toolbar").assertDoesNotExist()
        compose.onNodeWithText("选中文字后设置格式").assertDoesNotExist()
        compose.onNodeWithTag("phrase-detail-text").performTextReplacement("你好，朋友")
        compose.runOnIdle { model.content.editPhrase { it.copy(formats=listOf(
            com.kigtts.next.text.TextFormatRun(0,2,TextFormat.BOLD),
            com.kigtts.next.text.TextFormatRun(0,2,TextFormat.UNDERLINE))) } }
        compose.onNodeWithTag("phrase-type-AUDIO").performClick()
        compose.onNodeWithTag("phrase-choose-audio").performScrollTo().assertExists()
        compose.onNodeWithTag("save-phrase-details").assertIsNotEnabled()
        assertTrue(model.content.phraseDraft.value!!.richText)
        compose.onNodeWithTag("phrase-type-MEDIA").performClick()
        compose.onNodeWithTag("phrase-choose-media").performScrollTo().assertExists()
        compose.onNodeWithTag("phrase-type-PAGE").performClick()
        compose.onNodeWithTag("phrase-edit-html").performScrollTo().assertExists()
        compose.onNodeWithTag("phrase-format-toolbar").assertDoesNotExist()
        compose.onNodeWithTag("phrase-type-TEXT").performClick()
        compose.onNodeWithTag("phrase-detail-text").performScrollTo().assertTextContains("你好，朋友")
        assertTrue(model.content.phraseDraft.value!!.richText)
        compose.onNodeWithTag("save-phrase-details").performClick()
        compose.waitUntil(5000) { model.content.phraseDraft.value == null }
        val saved = model.phrases.value.last()
        assertTrue(saved.richText); assertEquals(2, saved.formats.size); assertEquals(QuickCardType.TEXT, saved.type)
        compose.runOnIdle { model.content.send(saved) }
        assertEquals("你好，朋友", model.state.value.editorText); assertEquals(saved.formats, model.state.value.subtitleFormats)
    }
    @Test fun customColorUsesSharedHsvDialogAndNoColorCanBeSavedWithoutChangingText() {
        val model = show()
        compose.onNodeWithTag("phrase-detail-text").performTextReplacement("颜色正文")
        compose.onNodeWithTag("phrase-appearance-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("phrase-color-mode").performScrollTo()
        compose.onNodeWithContentDescription("卡片颜色：自定义").performClick()
        compose.onNodeWithContentDescription("预设颜色 #6750A4").performScrollTo().performClick()
        compose.onNodeWithText("应用颜色").performClick()
        compose.waitUntil(5000) { model.content.phraseDraft.value?.colorMode == CardColorMode.CUSTOM }
        assertEquals(0xFF6750A4, model.content.phraseDraft.value!!.color)
        assertEquals(CardColorMode.CUSTOM, model.content.phraseDraft.value!!.colorMode)
        compose.onNodeWithContentDescription("卡片颜色：无颜色").performClick()
        compose.onNodeWithTag("save-phrase-details").performClick()
        compose.waitUntil(5000) { model.content.phraseDraft.value == null }
        val saved = model.phrases.value.last()
        assertEquals(CardColorMode.NONE, saved.colorMode); assertEquals("颜色正文", saved.text)
    }
}
