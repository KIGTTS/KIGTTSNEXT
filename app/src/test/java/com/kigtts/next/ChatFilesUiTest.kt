package com.kigtts.next

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.kigtts.next.theme.ThemeSettings
import com.kigtts.next.ui.plugins.ChatInputBar
import com.kigtts.next.theme.KigttsTheme
import com.kigtts.plugin.api.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class ChatFilesUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun fileOnlyDraftCanSendAndFileActionsRemainSeparateFromImages() {
        val actions = mutableListOf<Pair<String, Map<String,String>>>()
        val file = ChatFile(PluginResource("imports/test.mid"), "test.mid", 1024, openAction = "save")
        compose.setContent { KigttsTheme(ThemeSettings()) { Box(Modifier.width(393.dp)) {
            ChatInputBar(PageBlock.Chat(emptyList(), "send", "stop", imageAction = "image", fileAction = "file", draftFiles = listOf(file), removeFileAction = "remove"),
                true, { null }, { action, inputs -> actions += action to inputs }) {}
        } } }
        compose.onNodeWithText("test.mid").assertExists()
        compose.onNodeWithTag("plugin-chat-send").assertIsEnabled().performClick()
        compose.onNodeWithTag("plugin-chat-file").performClick()
        compose.onNodeWithContentDescription("移除 test.mid").performClick()
        assertEquals(listOf("send", "file", "remove"), actions.map { it.first })
        assertEquals("imports/test.mid", actions.last().second["value"])
    }
}
