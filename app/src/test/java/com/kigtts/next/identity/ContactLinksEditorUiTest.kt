package com.kigtts.next.identity

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kigtts.next.theme.*
import com.kigtts.next.ui.identity.ContactLinksEditor
import com.kigtts.next.ui.identity.ContactOrderList
import androidx.compose.ui.geometry.Offset
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
class ContactLinksEditorUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun handleDragCommitsOnceAndKeepsContactsAndButtonsAvailableAfterDrop() {
        var links by mutableStateOf(listOf(ContactLink("微信", "a"), ContactLink("QQ", "b"), ContactLink("Telegram", "c")))
        var writes = 0
        compose.setContent { KigttsTheme(ThemeSettings()) { ContactOrderList(links, { links = it; writes++ }, {}, { index -> links = links.filterIndexed { i, _ -> i != index } }) } }
        val grip = compose.onNodeWithContentDescription("拖动排序微信")
        val destination = compose.onNodeWithContentDescription("拖动排序Telegram").fetchSemanticsNode().boundsInRoot.center
        val start = grip.fetchSemanticsNode().boundsInRoot.center
        grip.performTouchInput { down(center); advanceEventTime(700); moveBy(destination - start, 400); up() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf("QQ", "Telegram", "微信"), links.map { it.label }); assertEquals(1, writes) }
        compose.onNodeWithContentDescription("移除QQ").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf("Telegram", "微信"), links.map { it.label }) }
    }
    @Test fun contactsCanBeAddedEditedAndRemovedIndividually() {
        var links by mutableStateOf(listOf(ContactLink("微信", "first")))
        compose.setContent { KigttsTheme(ThemeSettings()) { ContactLinksEditor(links) { links = it } } }
        compose.onNodeWithText("添加联系方式").performClick()
        compose.onNodeWithText("搜索平台").performTextInput("QQ")
        compose.onAllNodesWithText("QQ").filter(hasClickAction() and !hasSetTextAction()).onFirst().performClick()
        compose.onNodeWithText("账号或链接").performTextInput("12345")
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle { assertEquals(listOf(ContactLink("微信", "first"), ContactLink("QQ", "12345")), links) }
        compose.onNodeWithText("QQ").performClick()
        compose.onNodeWithText("账号或链接").performTextReplacement("67890")
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithContentDescription("移除微信").performClick()
        compose.runOnIdle { assertEquals(listOf(ContactLink("QQ", "67890")), links) }
    }

    @Test fun platformPickerSearchFindsNewServicesAndTheirGroupWithoutChangingExistingAccounts() {
        var links by mutableStateOf(emptyList<ContactLink>())
        compose.setContent { KigttsTheme(ThemeSettings()) { ContactLinksEditor(links) { links = it } } }
        compose.onNodeWithText("添加联系方式").performClick()
        compose.onNodeWithText("搜索平台").performTextInput("rednote")
        compose.onNodeWithText("社交媒体").assertIsDisplayed()
        compose.onNodeWithText("小红书").performClick()
        compose.onNodeWithText("账号或链接").performTextInput("my-profile")
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle { assertEquals(listOf(ContactLink("小红书", "my-profile")), links) }
        compose.onNodeWithText("添加联系方式").performClick()
        compose.onNodeWithText("搜索平台").performTextInput("apple music")
        compose.onNodeWithText("音视频创作").assertIsDisplayed()
        compose.onNodeWithText("Apple Music").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(1, links.size) }
    }
}
