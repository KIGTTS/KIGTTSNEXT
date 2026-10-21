package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.cards.CardEditorPanelScaffold
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w1000dp-h1000dp-xhdpi")
class CardEditorCollapseUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun portraitPanelReallyReachesHandleAndTabsInsteadOfRetainingOneHundredTwentyDpOfTools() {
        compose.setContent {
            Box(Modifier.size(393.dp, 820.dp).testTag("stage")) {
                CardEditorPanelScaffold(.8f, true, false, {}, { Box(it.height(300.dp)) }, {
                    Text("工具内容", Modifier.height(600.dp))
                }, categories = { _, _ -> Text("分类", Modifier.height(72.dp)) })
            }
        }
        compose.onNodeWithTag("toggle-card-editor-panel").performClick()
        compose.waitForIdle()
        val stage = compose.onNodeWithTag("stage").getUnclippedBoundsInRoot()
        val header = compose.onNodeWithTag("card-editor-panel-header").getUnclippedBoundsInRoot()
        assertEquals(stage.bottom.value - 128f, header.top.value, 1f)
        compose.onNodeWithTag("toggle-card-editor-panel").performClick()
        compose.waitForIdle()
        assertTrue(compose.onNodeWithTag("card-editor-panel-header").getUnclippedBoundsInRoot().top < header.top)
        compose.onNodeWithTag("toggle-card-editor-panel").performClick()
        assertEquals(header.top.value, compose.onNodeWithTag("card-editor-panel-header").getUnclippedBoundsInRoot().top.value, 1f)
    }
}
