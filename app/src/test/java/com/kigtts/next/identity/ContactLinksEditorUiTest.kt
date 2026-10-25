package com.kigtts.next.identity

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kigtts.next.theme.*
import com.kigtts.next.ui.identity.ContactLinksEditor
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
    @Test fun contactsCanBeAddedEditedAndRemovedIndividually() {
        var links by mutableStateOf(listOf(ContactLink("微信", "first")))
        compose.setContent { KigttsTheme(ThemeSettings()) { ContactLinksEditor(links) { links = it } } }
        compose.onNodeWithText("添加联系方式").performClick()
        compose.onNodeWithText("平台或名称").performTextInput("QQ")
        compose.onNodeWithText("账号或链接").performTextInput("12345")
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle { assertEquals(listOf(ContactLink("微信", "first"), ContactLink("QQ", "12345")), links) }
        compose.onNodeWithText("QQ").performClick()
        compose.onNodeWithText("账号或链接").performTextReplacement("67890")
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithContentDescription("移除微信").performClick()
        compose.runOnIdle { assertEquals(listOf(ContactLink("QQ", "67890")), links) }
    }
}
