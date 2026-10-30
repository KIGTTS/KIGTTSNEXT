package com.kigtts.next.sharing

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.identity.ProfileTile
import com.kigtts.next.identity.ProfileTileKind
import com.kigtts.next.testAppModel
import com.kigtts.next.theme.*
import com.kigtts.next.ui.cards.CardPreviewDialog
import com.kigtts.plugin.api.PolaroidCard
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
class FaceUnifiedShareUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun discoveryBarScrollsHorizontallyAndRoutesSystemWifiAndPeerActionsSeparately() {
        var shares = 0; var wifi = 0; var sent: String? = null
        val peers = (1..8).map { FacePeer("peer$it", "朋友$it", "192.168.1.$it", 12345, faceToken()) }
        compose.setContent { KigttsTheme(ThemeSettings()) {
            FaceDiscoveryBar(peers, false, { shares++ }, { wifi++ }, { sent = it.id })
        } }
        compose.onNodeWithTag("face-system-share").performClick(); assertEquals(1, shares)
        compose.onNodeWithTag("face-wifi-share").performClick(); assertEquals(1, wifi)
        compose.onNodeWithTag("face-discovery-bar").performScrollToNode(hasTestTag("face-peer-peer8"))
        compose.onNodeWithTag("face-peer-peer8").performClick(); assertEquals("peer8", sent)
        assertEquals(1, shares); assertEquals(1, wifi)
    }

    @Test fun photoSharingOpensStraightToDiscoveryAndReplacesIdentityWithAStack() {
        val model = testAppModel(compose.activity.application)
        compose.activity.viewModelStore.put("unified-share", model)
        val person = model.identities.create().copy(nickname = "自己的名片")
        model.identities.save(person)
        val photos = (1..3).map { model.cards.create(PolaroidCard(title = "照片$it")) }
        model.faceShare.openSend(photos.map { it.id })
        assertEquals(FaceMode.SEND, model.faceShare.panel.value?.mode)
        try {
            compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) { FaceShareHost(model) } }
            compose.mainClock.advanceTimeBy(600); compose.waitForIdle()
            compose.onNodeWithText("分享照片").assertIsDisplayed()
            compose.onNodeWithTag("face-share-identity").assertDoesNotExist()
            (0..2).forEach { compose.onNodeWithTag("face-share-photo-$it", useUnmergedTree = true).assertExists() }
            compose.onNodeWithTag("face-system-share").assertIsDisplayed()
            compose.onNodeWithTag("face-wifi-share").assertIsDisplayed()
            compose.onNodeWithTag("face-share").captureRoboImage("build/previews/face-unified-photo-share.png")
        } finally { compose.runOnIdle { model.faceShare.stop() } }
    }

    @Test fun personalSharingShowsOnlyTheCardAndNoProfileTiles() {
        val model = testAppModel(compose.activity.application)
        compose.activity.viewModelStore.put("identity-share", model)
        val person = model.identities.create().copy(nickname = "我的名片", tiles = listOf(
            ProfileTile(kind = ProfileTileKind.TEXT, title = "不应显示的磁贴")))
        model.identities.save(person); model.faceShare.openSend(listOf(person.designId))
        try {
            compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) { FaceShareHost(model) } }
            compose.mainClock.advanceTimeBy(600); compose.waitForIdle()
            compose.onNodeWithTag("face-share-identity").assertIsDisplayed()
            compose.onNodeWithTag("face-share-photos").assertDoesNotExist()
            compose.onNodeWithText("不应显示的磁贴").assertDoesNotExist()
            compose.onNodeWithText("拍立得成片").assertDoesNotExist()
            compose.onNodeWithTag("face-system-share").assertIsDisplayed()
            compose.onNodeWithTag("face-share").captureRoboImage("build/previews/face-unified-identity-share.png")
        } finally { compose.runOnIdle { model.faceShare.stop() } }
    }

    @Test fun receivingChangesKeepPhotoPreviewFormatActionsAndSheetPosition() {
        val model = testAppModel(compose.activity.application)
        compose.activity.viewModelStore.put("share-isolation", model)
        val photos = (1..2).map { model.cards.create(PolaroidCard(title = "照片$it")) }
        model.faceShare.openSend(photos.map { it.id })
        try {
            compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) { FaceShareHost(model) } }
            compose.mainClock.advanceTimeBy(600); compose.waitForIdle()
            val before = compose.onNodeWithTag("face-share").getUnclippedBoundsInRoot()
            compose.onNodeWithContentDescription("分享格式：原图").performClick()
            compose.runOnIdle { model.faceShare.openReceive() }
            compose.onNodeWithText("分享照片").assertIsDisplayed()
            compose.onNodeWithText("面对面接收").assertDoesNotExist()
            compose.onNodeWithContentDescription("分享格式：原图").assertIsSelected()
            compose.runOnIdle { model.faceShare.setReceiving(false) }
            compose.onNodeWithContentDescription("分享格式：原图").assertIsSelected()
            (0..1).forEach { compose.onNodeWithTag("face-share-photo-$it", useUnmergedTree = true).assertExists() }
            compose.onNodeWithTag("face-system-share").assertIsDisplayed()
            compose.onNodeWithTag("face-wifi-share").assertIsDisplayed()
            val after = compose.onNodeWithTag("face-share").getUnclippedBoundsInRoot()
            assertEquals(before, after)
        } finally { compose.runOnIdle { model.faceShare.stop() } }
    }

    @Test fun ordinaryPhotoShareButtonUsesUnifiedPanelInsteadOfBypassingIt() {
        val photo = PolaroidCard(title = "照片")
        var selected = emptyList<String>(); var systemDirect = false
        compose.setContent { KigttsTheme(ThemeSettings()) {
            CompositionLocalProvider(LocalFaceShare provides FaceShareActions({ selected = it }, {})) {
                CardPreviewDialog(photo, { null }, null, {}, {}, { systemDirect = true }, {}, {}, {})
            }
        } }
        compose.mainClock.advanceTimeBy(800); compose.waitForIdle()
        compose.onNodeWithTag("share-card-preview").performClick()
        assertEquals(listOf(photo.id), selected); assertFalse(systemDirect)
    }
}
