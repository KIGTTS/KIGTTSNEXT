package com.kigtts.next.identity

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.testAppModel
import com.kigtts.next.theme.*
import com.kigtts.next.ui.identity.PersonalCardsScreen
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
    @Test fun rememberedCardSwipesAndItsInformationAndContactEntriesRemainUsable() {
        val model = testAppModel(ApplicationProvider.getApplicationContext<Application>())
        compose.activity.viewModelStore.put("personal-test", model)
        val first = model.identities.create().copy(nickname = "第一张名片")
        val second = model.identities.create().copy(nickname = "第二张名片", bio = "很高兴认识你", contacts = listOf(ContactLink("微信", "example")))
        model.identities.save(first); model.identities.save(second); model.identities.select(second.id)
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) { PersonalCardsScreen(model) } }
        compose.onNodeWithText("2 / 2").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/previews/personal-cards.png")
        compose.onNode(hasContentDescription("联系方式：微信") and hasAnyAncestor(hasTestTag("personal-card-${second.id}"))).performClick()
        compose.onNodeWithText("example").assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithText("编辑个人信息").performClick()
        compose.onNodeWithText("昵称").performTextReplacement("新的昵称")
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(5000) { model.identities.current?.nickname == "新的昵称" }
        assertEquals("新的昵称", model.identities.current?.nickname)
        compose.onNodeWithTag("personal-cards-pager").performTouchInput { swipeRight() }
        compose.onNodeWithText("1 / 2").assertIsDisplayed()
        assertEquals(first.id, model.identities.current?.id)
        assertEquals("新的昵称", model.identities.current?.nickname)
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
        assertTrue(collapsed.top.value - before.top.value <= 25f)
        compose.onRoot().captureRoboImage("build/previews/personal-card-background-editor.png")
    }
}
