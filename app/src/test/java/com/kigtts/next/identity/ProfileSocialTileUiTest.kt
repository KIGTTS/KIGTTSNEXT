package com.kigtts.next.identity

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kigtts.next.CardPrintCoordinatorTest
import com.kigtts.next.theme.*
import com.kigtts.next.ui.identity.ProfileTileEditor
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED) @Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class ProfileSocialTileUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun newSocialTileCanChooseAPlatformWithoutCreatingAProfileContactAndReopenForEditing() {
        val fixture = CardPrintCoordinatorTest.Fixture()
        try {
            var visible by mutableStateOf(true)
            var current by mutableStateOf<ProfileTile?>(null)
            var saved: ProfileTile? = null
            compose.setContent { KigttsTheme(ThemeSettings()) {
                if (visible) ProfileTileEditor(current, emptyList(), fixture.cards.stickers, { visible = false }) { saved = it; visible = false }
            } }
            compose.onNodeWithText("社媒").performClick()
            compose.onNodeWithText("请先添加联系方式").assertDoesNotExist()
            compose.onNodeWithText("选择社媒平台").performClick()
            compose.onNodeWithText("搜索平台").performTextInput("rednote")
            compose.onNodeWithText("小红书").performClick()
            compose.onNodeWithText("账号或链接").performTextInput("https://www.xiaohongshu.com/user/profile/test")
            compose.onNodeWithText("保存").performClick()
            compose.onNodeWithText("完成").performClick()
            compose.runOnIdle {
                assertEquals(ProfileTileKind.CONTACT, saved!!.kind)
                assertEquals(SocialPlatform.XIAOHONGSHU, socialPlatform(saved!!.social!!))
                assertTrue(saved!!.contact.isEmpty())
                current = ProfileTile.from(saved!!.json()); visible = true
            }
            compose.onNodeWithText("小红书").performClick()
            compose.onNodeWithText("账号或链接").performTextReplacement("https://xhslink.com/updated")
            compose.onNodeWithText("保存").performClick()
            compose.onNodeWithText("完成").performClick()
            compose.runOnIdle { assertEquals("https://xhslink.com/updated", saved!!.social!!.value) }
        } finally { fixture.scope.cancel() }
    }

    @Test fun existingContactCanStillBeSelectedAndCancellingNewPlatformDoesNotReplaceIt() {
        val fixture = CardPrintCoordinatorTest.Fixture()
        try {
            val qq = ContactLink("QQ", "12345")
            var saved: ProfileTile? = null
            compose.setContent { KigttsTheme(ThemeSettings()) {
                ProfileTileEditor(null, listOf(qq), fixture.cards.stickers, {}) { saved = it }
            } }
            compose.onNodeWithText("社媒").performClick()
            compose.onNodeWithText("QQ").performClick()
            compose.onNodeWithText("更换社媒平台").performClick()
            compose.onNodeWithText("搜索平台").performTextInput("github")
            compose.onNodeWithText("GitHub").performClick()
            compose.onAllNodesWithText("取消").onLast().performClick()
            compose.onNodeWithText("完成").performClick()
            compose.runOnIdle {
                assertNull(saved!!.social)
                assertEquals(qq, saved!!.resolveContact(listOf(qq)))
            }
        } finally { fixture.scope.cancel() }
    }
}
