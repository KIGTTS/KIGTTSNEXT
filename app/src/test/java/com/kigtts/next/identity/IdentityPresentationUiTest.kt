package com.kigtts.next.identity

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.testAppModel
import com.kigtts.next.theme.*
import com.kigtts.next.ui.cards.PolaroidCanvas
import com.kigtts.next.ui.identity.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED) @Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class IdentityPresentationUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun shapePickerScrollsToLaterMaterialShapesAndSavesTheChosenShape() {
        val model = testAppModel(compose.activity.application)
        compose.activity.viewModelStore.put("identity-shape-picker", model)
        var saved: IdentityCard? = null
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
            PersonalInformationDialog(IdentityCard(designId = "preview", nickname = "你好"), model.stickers, {}, { saved = it })
        } }
        compose.onNodeWithTag("avatar-shape/circle").assertIsSelected()
        compose.onNodeWithTag("avatar-shapes").performScrollToNode(hasTestTag("avatar-shape/heart"))
        compose.onNodeWithTag("avatar-shape/heart").performClick().assertIsSelected()
        compose.onRoot().captureRoboImage("build/previews/identity-avatar-shapes.png")
        compose.onNodeWithText("保存").performClick()
        assertEquals("heart", saved?.avatarFrame)
    }

    @Test fun editorPhotoPoseChangesBeforeFingersAreReleased() {
        val model = testAppModel(compose.activity.application)
        compose.activity.viewModelStore.put("identity-live-preview", model)
        val f = com.kigtts.next.CardPrintCoordinatorTest.Fixture()
        val image = model.stickers.importResource(f.photo())
        val person = model.identities.create()
        val design = requireNotNull(model.stickers.identityDesigns.get(person.designId)).copy(imageId = image.id)
        var draft by mutableStateOf(design)
        val painter = runBlocking { model.stickers.identityBackgrounds.prepare(design, person) }
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Box(Modifier.width(360.dp)) {
                PolaroidCanvas(draft, model.stickers::imageFile, Modifier.fillMaxWidth(),
                    photoEditable = true, onPhotoChange = { draft = it },
                    photoContent = { IdentityLiveBackground(draft, painter) })
            }
        } }
        val photo = compose.onNodeWithTag("card-photo/${design.id}")
        photo.performTouchInput {
            down(0, center - Offset(35f, 0f)); down(1, center + Offset(35f, 0f))
            moveBy(0, Offset(-35f, 0f)); moveBy(1, Offset(35f, 0f))
        }
        assertTrue("编辑预览不可等松手或等重新烘焙", draft.photoZoom > 1.1)
        photo.performTouchInput { up(0); up(1) }
        f.scope.cancel()
    }

    @Test fun sixthContactIsAnOverflowButtonAndOpensTheRemainingContactQr() {
        val model = testAppModel(compose.activity.application)
        compose.activity.viewModelStore.put("identity-contact-overflow", model)
        val links = listOf("QQ", "微信", "邮箱", "GitHub", "B站", "Telegram", "Discord", "X").mapIndexed { index, name -> ContactLink(name, "account$index") }
        var contacts by mutableStateOf(links)
        var opened by mutableStateOf<ContactLink?>(null)
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
            Box(Modifier.fillMaxWidth()) {
                PersonalContactButtons(contacts, true, { opened = it })
                opened?.let { PersonalContactDialog(it) { opened = null } }
            }
        } }
        for (index in 0..4) compose.onNodeWithTag("personal-contact-$index").assertIsDisplayed()
            .assertWidthIsEqualTo(32.dp).assertHeightIsEqualTo(32.dp)
        for (index in 1..4) {
            val previous = compose.onNodeWithTag("personal-contact-${index - 1}").getUnclippedBoundsInRoot()
            val current = compose.onNodeWithTag("personal-contact-$index").getUnclippedBoundsInRoot()
            assertTrue("相邻底框必须留出间隔", current.left - previous.right >= 4.dp)
        }
        compose.onNodeWithTag("personal-contact-5").assertDoesNotExist()
        compose.onNodeWithTag("personal-contact-more").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/previews/identity-contact-buttons.png")
        compose.onNodeWithTag("personal-contact-0").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(100)
        compose.onRoot().captureRoboImage("build/previews/identity-contact-buttons-pressed.png")
        compose.onNodeWithTag("personal-contact-0").performTouchInput { cancel() }
        compose.onNodeWithTag("personal-contact-more").performClick()
        compose.onNodeWithTag("personal-contact-overflow-5").performClick()
        compose.onNodeWithText("account5").assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()
        compose.runOnIdle { contacts = links.take(5) }
        compose.onNodeWithTag("personal-contact-more").assertDoesNotExist()
    }
}
