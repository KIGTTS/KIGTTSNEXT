package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.state.*
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
class QuickPhrasePresentationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun show(): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("appearance", "home-panel", "quick-phrases", "media-presentation").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        return testAppModel(app).also { model ->
            val first = model.phrases.value[0]; val second = model.phrases.value[1]
            model.quickTexts.edit(first.id, "第一行正文\n第二行正文", "", "常用")
            model.quickTexts.edit(second.id, "你好，能请你帮忙吗？", "寻求帮助", "常用")
            compose.setContent { KigttsApp(model) }
            compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT); model.panelHeight(340f) }
        }
    }
    private fun layout(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }
    @Test fun plainContentUsesTwoLargeLinesAndTitledContentUsesTheSameHierarchyInBothLayoutsAndEditing() {
        val model = show(); val plain = model.phrases.value[0]; val titled = model.phrases.value[1]
        fun verify() {
            compose.onNodeWithTag("phrase-main-" + plain.id, useUnmergedTree = true).assertTextEquals(plain.text)
            compose.onNodeWithTag("phrase-content-" + plain.id, useUnmergedTree = true).assertDoesNotExist()
            assertEquals(2, layout("phrase-main-" + plain.id).lineCount)
            compose.onNodeWithTag("phrase-main-" + titled.id, useUnmergedTree = true).assertTextEquals(titled.title)
            compose.onNodeWithTag("phrase-content-" + titled.id, useUnmergedTree = true).assertTextEquals(titled.text)
            assertTrue(layout("phrase-main-" + titled.id).layoutInput.style.fontSize.value > layout("phrase-content-" + titled.id).layoutInput.style.fontSize.value)
            compose.onNodeWithText("点击填入文本", useUnmergedTree = true).assertDoesNotExist()
        }
        verify(); compose.onRoot().captureRoboImage("build/previews/quick-phrase-content-title.png")
        compose.onNodeWithTag("quick-phrase-" + titled.id).performClick()
        assertEquals(titled.text, model.state.value.editorText)
        compose.runOnIdle { model.panelHeight(176f) }; verify()
        compose.onNodeWithContentDescription("编辑快捷文本").performClick(); verify()
        val tile = compose.onNodeWithTag("edit-phrase-" + plain.id).getUnclippedBoundsInRoot()
        assertEquals(88f, (tile.bottom - tile.top).value, .5f)
    }
    @Test fun clearingOptionalTitleInTheFullscreenFormRestoresContentOnlyAndSurvivesRestart() {
        val model = show(); val item = model.phrases.value[1]
        compose.onNodeWithContentDescription("编辑快捷文本").performClick()
        compose.onNodeWithTag("edit-phrase-" + item.id).performClick()
        compose.onNodeWithText("文本内容").assertIsDisplayed()
        compose.onNodeWithText("缩略标题（可选）").assertIsDisplayed()
        compose.onNodeWithTag("phrase-detail-title").performTextClearance()
        compose.onNodeWithTag("save-phrase-details").performClick()
        compose.waitUntil(5000) { model.content.phraseDraft.value == null }
        compose.onNodeWithTag("phrase-main-" + item.id, useUnmergedTree = true).assertTextEquals(item.text)
        compose.onNodeWithTag("phrase-content-" + item.id, useUnmergedTree = true).assertDoesNotExist()
        assertEquals("", testAppModel(ApplicationProvider.getApplicationContext()).phrases.value.first { it.id == item.id }.title)
        assertEquals(item.text, model.phrases.value.first { it.id == item.id }.text)
    }
}
