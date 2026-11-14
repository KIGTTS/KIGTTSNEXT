package com.kigtts.next.identity

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.testAppModel
import com.kigtts.next.theme.*
import com.kigtts.next.ui.identity.PersonalCardsScreen
import com.kigtts.next.state.Destination
import com.kigtts.next.ui.navigation.SecondaryDestination
import com.kigtts.next.ui.cards.CardEditorScreen
import com.kigtts.next.ui.cards.CardPageScaffold
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
class PersonalCardsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun singleProfileKeepsInformationAndContactEntriesUsableWithoutPresetControls() {
        val model = testAppModel(ApplicationProvider.getApplicationContext<Application>())
        compose.activity.viewModelStore.put("personal-test", model)
        val second = model.identities.create().copy(nickname = "我的名片", bio = "很高兴认识你", contacts = listOf(ContactLink("微信", "example")), tags = listOf("摄影", "开发"))
        model.identities.save(second)
        model.navigate(Destination.PersonalCards)
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
            com.kigtts.next.sharing.CloudSharingSetupHost(model)
            val state by model.state.collectAsState()
            SecondaryDestination(state.destination, state, model)
        } }
        compose.onNodeWithTag("personal-cards-pager").assertDoesNotExist()
        compose.onNodeWithText("添加名片背景").assertDoesNotExist()
        compose.onRoot().captureRoboImage("build/previews/personal-cards.png")
        compose.onNode(hasContentDescription("联系方式：微信") and hasAnyAncestor(hasTestTag("personal-card-${second.id}"))).performClick()
        compose.onNodeWithText("example").assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithText("头像与昵称").assertDoesNotExist()
        compose.onNodeWithText("编辑磁贴").assertDoesNotExist()
        compose.onNodeWithText("编辑标签").assertDoesNotExist()
        compose.onNodeWithTag("edit-personal-information").performClick()
        compose.runOnIdle { assertEquals(Destination.PersonalCards, model.state.value.destination) }
        compose.onRoot().captureRoboImage("build/previews/personal-edit-mode.png")
        compose.onNodeWithTag("profile-tag-remove/摄影").assertExists().performClick()
        compose.onNodeWithTag("profile-edit-information").performClick()
        compose.onNodeWithText("昵称").performTextReplacement("新的昵称")
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithTag("edit-personal-information").performClick()
        compose.waitUntil(5000) { model.identities.current?.nickname == "新的昵称" }
        assertEquals("新的昵称", model.identities.current?.nickname)
        assertEquals(second.id, model.identities.current?.id)
        assertEquals(1, model.identities.items.value.size)
        assertEquals(listOf("开发"), model.identities.current?.tags)
        compose.onNodeWithTag("profile-add-tile").assertDoesNotExist()
        compose.onNodeWithTag("profile-tag-remove/开发").assertDoesNotExist()
        compose.onNodeWithText("分享名片").performScrollTo().performClick()
        compose.onNodeWithText("使用云端互传？").assertIsDisplayed()
        compose.onNodeWithTag("face-share").assertDoesNotExist()
        compose.runOnIdle { model.sharing.setup.dismiss() }
    }
    @Test fun backgroundEditorPreviewsIdentityWhileItsToolPanelRemainsUsable() {
        val model = testAppModel(ApplicationProvider.getApplicationContext<Application>())
        compose.activity.viewModelStore.put("personal-editor-test", model)
        val identity = model.identities.create().copy(nickname = "名片预览", bio = "你好", contacts = listOf(ContactLink("QQ", "12345")))
        model.identities.save(identity)
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
            CardPageScaffold("编辑名片背景", {}, subtitle = null, avoidKeyboard = false) {
                CardEditorScreen(model.cards, model.stickers, identity.designId, {}, identity = identity)
            }
        } }
        compose.onNodeWithText("名片预览").assertIsDisplayed()
        val before = compose.onNodeWithTag("polaroid/" + identity.designId).getUnclippedBoundsInRoot()
        compose.onNodeWithTag("toggle-card-editor-panel").performClick()
        compose.onNodeWithText("名片预览").assertIsDisplayed()
        val collapsed = compose.onNodeWithTag("polaroid/" + identity.designId).getUnclippedBoundsInRoot()
        assertTrue(collapsed.top > before.top)
        assertTrue("展开抽屉时预览应明显上移", collapsed.top.value - before.top.value > 25f)
        compose.onRoot().captureRoboImage("build/previews/personal-card-background-editor.png")
    }
}
