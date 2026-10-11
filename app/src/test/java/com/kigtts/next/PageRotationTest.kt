package com.kigtts.next

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.state.Destination
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class PageRotationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun activityRecreationRetainsThePageAndDoesNotAnimateItInAgain() {
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { model.navigate(Destination.Settings) }
        compose.mainClock.advanceTimeBy(600); compose.waitForIdle()
        val page = compose.onNodeWithTag("page-layer/1/settings")
        assertEquals(0f, page.getUnclippedBoundsInRoot().left.value, .1f)
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertSame(model, ViewModelProvider(compose.activity)[AppViewModel::class.java])
        assertEquals(Destination.Settings, model.state.value.destination)
        assertEquals("Recreated page is in place before a new animation frame", 0f, page.getUnclippedBoundsInRoot().left.value, .1f)
        repeat(8) {
            compose.mainClock.advanceTimeBy(32); compose.waitForIdle()
            assertEquals(0f, page.getUnclippedBoundsInRoot().left.value, .1f)
        }
        compose.mainClock.autoAdvance = true
    }

    @Test fun phraseEditorPageSurvivesRecreationWithoutRepeatingTheEntrance() {
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.runOnIdle { model.openPanel(com.kigtts.next.state.HomePanel.QUICK_TEXT); model.editQuickTexts() }
        compose.onNodeWithTag("edit-phrase-" + model.phrases.value.first().id).performClick()
        compose.onNodeWithTag("phrase-detail-text").performTextReplacement("旋转保留草稿")
        compose.onNodeWithTag("phrase-detail-title").performTextInput("缩略标题草稿")
        compose.mainClock.autoAdvance = false
        compose.activityRule.scenario.recreate()
        compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
        compose.onNodeWithTag("phrase-detail-text").assertTextContains("旋转保留草稿")
        compose.onNodeWithTag("phrase-detail-title").assertTextContains("缩略标题草稿")
        compose.onNode(isDialog()).assertDoesNotExist()
        assertTrue(model.state.value.destination is Destination.PhraseEditor)
        val token = model.content.phraseDraft.value!!.key
        assertEquals(0f, compose.onNodeWithTag("page-layer/1/phrase-editor/" + token).getUnclippedBoundsInRoot().left.value, .1f)
        compose.mainClock.autoAdvance = true
    }
}
