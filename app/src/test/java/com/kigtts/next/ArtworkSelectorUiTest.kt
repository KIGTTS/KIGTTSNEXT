package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kigtts.next.theme.*
import com.kigtts.next.ui.artwork.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class ArtworkSelectorUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun asyncInitialSelectionSurvivesLoadingAndPreviewCanSelectTheNextImage() {
        var entries by mutableStateOf(emptyList<ArtworkChoice>())
        var chosen: List<String> = emptyList()
        compose.setContent { KigttsTheme(ThemeSettings()) {
            ArtworkSelectorPage("选择拍立得", entries, true, listOf("one"), {}, { chosen = it }) { item, modifier, _ ->
                Box(modifier) { Text(item.title) }
            }
        } }
        compose.runOnIdle { entries = listOf(ArtworkChoice("one", "第一张"), ArtworkChoice("two", "第二张")) }
        compose.onNodeWithText("选择 (1)").assertIsEnabled()
        compose.onNodeWithTag("artwork/one").performClick()
        compose.onNodeWithContentDescription("下一张").performClick()
        compose.waitForIdle()
        compose.onNode(hasContentDescription("选择照片") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithContentDescription("关闭预览").performClick()
        compose.onNodeWithText("选择 (2)").performClick()
        assertEquals(listOf("one", "two"), chosen)
    }
}
