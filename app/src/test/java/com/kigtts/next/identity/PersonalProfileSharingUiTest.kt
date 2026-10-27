package com.kigtts.next.identity

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.testAppModel
import com.kigtts.next.theme.*
import com.kigtts.next.recovery.*
import com.kigtts.next.sharing.*
import com.kigtts.next.ui.identity.*
import com.kigtts.next.ui.navigation.SafeModeBrand
import org.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED) @Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class PersonalProfileSharingUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun faceShareButtonIsSeparateFromTheConnectedContacts() {
        val model = testAppModel(compose.activity.application)
        compose.activity.viewModelStore.put("profile-sharing", model)
        val card = model.identities.create().copy(nickname = "你好", contacts = listOf(ContactLink("QQ", "12345")))
        var sent = emptyList<String>()
        compose.setContent { KigttsTheme(ThemeSettings()) {
            CompositionLocalProvider(LocalFaceShare provides FaceShareActions({ sent = it }, {})) {
                Box(Modifier.width(360.dp)) { PersonalCardView(card, model.stickers) }
            }
        } }
        compose.onNodeWithTag("personal-face-share").assertWidthIsEqualTo(32.dp).assertHeightIsEqualTo(32.dp)
        compose.onNodeWithTag("personal-contact-0").assertWidthIsEqualTo(32.dp).assertHeightIsEqualTo(32.dp)
        compose.onRoot().captureRoboImage("build/previews/personal-card-compact-corners.png")
        compose.onNodeWithContentDescription("开启面对面").performClick()
        assertEquals(listOf(card.designId), sent)
        compose.onNodeWithContentDescription("联系方式：QQ").performClick()
        compose.onNodeWithText("12345").assertIsDisplayed()
    }
    @Test fun receivedDetailsKeepTheContactQrPayloadAndCanBeClosed() {
        val profile = JSONObject().put("nickname", "新朋友").put("bio", "很高兴认识你")
            .put("contacts", JSONArray().put(JSONObject().put("label", "微信").put("value", "account").put("qrContent", "https://example.com/contact")))
        assertEquals("https://example.com/contact", contactPayload(PublicIdentityProfile.from(profile).contacts.single()))
        compose.setContent { KigttsTheme(ThemeSettings()) { FaceProfileContent(profile) } }
        compose.onNodeWithText("新朋友").assertIsDisplayed()
        compose.onNodeWithTag("face-profile-contact-0").performClick()
        compose.onNode(hasText("account") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()
        compose.mainClock.advanceTimeBy(500); compose.waitForIdle()
        compose.onNodeWithTag("face-profile-contact-0").assertIsDisplayed()
    }
    @Test fun safeModeOffersTheCorrectThreeActionsAndALabelledSmallerLogo() {
        var entered = false
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Column { SafeModeBrand(true); SafeModePrompt(SafeModeStatus(true), {}, {}, { entered = true }) }
        } }
        compose.onNodeWithText("恢复插件").assertIsDisplayed()
        compose.onNodeWithText("进入插件管理").assertIsDisplayed()
        compose.onNodeWithText("进入软件").performClick()
        compose.mainClock.advanceTimeBy(500); compose.waitForIdle(); assertTrue(entered)
        compose.onNodeWithTag("safe-mode-label").assertIsDisplayed()
        compose.onNodeWithText("退出安全模式").assertDoesNotExist()
    }
}
