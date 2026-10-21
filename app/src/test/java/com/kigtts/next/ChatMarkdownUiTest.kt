package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.ui.plugins.ChatRichText
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class ChatMarkdownUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun tablesScrollAndFencedCodeCopiesAfterStreamingCompletes() {
        var text by mutableStateOf("## 对比\n\n| 名称 | 内容较长的说明 | 数量 | 备注 |\n|:---|:---:|---:|---|\n| A | 中文说明 | 2 | 稳定 |\n\n> **提示**：支持表格与代码\n\n- [x] 完成\n- [ ] 待办\n\n```kotlin\nval value = 1\n")
        var clipboard: androidx.compose.ui.platform.ClipboardManager? = null
        compose.setContent { MaterialTheme {
            clipboard = LocalClipboardManager.current
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { ChatRichText(text) }
        } }
        compose.onNodeWithTag("chat-markdown-table").assertIsDisplayed().performTouchInput { swipeLeft() }
        compose.onNodeWithText("☑").assertIsDisplayed()
        compose.onNodeWithContentDescription("复制代码").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("val value = 1\n", clipboard?.getText()?.text); text += "```\n\n**结束**" }
        compose.onNodeWithText("结束").performScrollTo().assertIsDisplayed()
        java.io.File("build/previews").mkdirs()
        compose.onRoot().captureRoboImage("build/previews/agent-markdown-table-code.png")
    }
}
