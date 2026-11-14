package com.kigtts.next.sharing

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.identity.ProfileTile
import com.kigtts.next.identity.ProfileTileKind
import com.kigtts.next.testAppModel
import com.kigtts.next.theme.*
import com.kigtts.next.ui.cards.CardPreviewDialog
import com.kigtts.next.ui.settings.*
import com.kigtts.plugin.api.PolaroidCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi", shadows = [FaceWindowsFileProvider::class])
class FaceUnifiedShareUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun receivingSettingsKeepTheirListGapAndDoNotOfferHotspotCreation() {
        val model = testAppModel(compose.activity.application)
        compose.activity.viewModelStore.put("receiving-settings", model)
        try {
            compose.setContent { KigttsTheme(ThemeSettings()) {
                SettingsGroup("面对面") {
                    FaceReceivingSetting(model, SettingPosition.FIRST)
                    SettingItem("扩列收藏夹", "", null, SettingPosition.LAST, Modifier.testTag("favorites"))
                }
            } }
            fun checkRows() {
                val receiver = compose.onNodeWithContentDescription("面对面接收").getUnclippedBoundsInRoot()
                val favorites = compose.onNodeWithTag("favorites").getUnclippedBoundsInRoot()
                assertEquals(2f, favorites.top.value - receiver.bottom.value, .01f)
                compose.onNodeWithText("连接设备").assertDoesNotExist()
                compose.onNodeWithText("创建临时热点").assertDoesNotExist()
            }
            checkRows()
            compose.onNodeWithContentDescription("面对面接收").performClick()
            compose.waitUntil(3000) { model.sharing.setup.state.value != null }
            compose.onNodeWithContentDescription("面对面接收").assertIsOff()
        } finally { compose.runOnIdle { model.sharing.setup.dismiss() } }
    }

    @Test fun resolvedPeerShowsItsNicknamePhotoAndActualAvatarShapeAfterSparseUpdates() {
        val file = java.io.File(compose.activity.cacheDir, "discovery-avatar.jpg")
        val source = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
        source.eraseColor(android.graphics.Color.rgb(220, 25, 110))
        file.outputStream().use { source.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it) }; source.recycle()
        val raw = FacePeer(faceToken(), "KIGTTS 用户", "192.168.1.2", 12345, faceToken())
        val resolved = raw.copy(name = "我的朋友", avatar = faceAvatarData(file), avatarFrame = "flower")
        var peers by mutableStateOf(listOf(raw)); var sent: FacePeer? = null
        compose.setContent { KigttsTheme(ThemeSettings()) { FaceDiscoveryBar(peers, false, {}, {}, { sent = it }) } }
        compose.runOnIdle { peers = listOf(resolved) }
        compose.onNodeWithText("我的朋友").assertIsDisplayed()
        compose.onNodeWithTag("face-peer-${raw.id}").performClick(); assertEquals("我的朋友", sent?.name)
        fun capture(name: String): android.graphics.Bitmap {
            val path = "build/previews/face-discovery-$name.png"
            compose.onNodeWithTag("face-peer-${raw.id}").captureRoboImage(path)
            return android.graphics.BitmapFactory.decodeFile(path)
        }
        val flower = capture("flower")
        val center = flower.getPixel(flower.width / 2, flower.height / 2)
        assertTrue(android.graphics.Color.red(center) > 200 && android.graphics.Color.green(center) < 45)
        compose.runOnIdle { peers = listOf(resolved.copy(avatarFrame = "circle")) }
        val circle = capture("circle")
        val changed = (0 until flower.height).sumOf { y -> (0 until flower.width).count { x -> flower.getPixel(x, y) != circle.getPixel(x, y) } }
        assertTrue("头像框应实际改变轮廓", changed > flower.width * flower.height / 50)
        flower.recycle(); circle.recycle(); file.delete()
    }

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

    @Test fun personalSharingUsesSystemChooserWithReadablePdfAndDoesNotLaunchLegacySheet() {
        val app = compose.activity.application
        val model = testAppModel(app)
        compose.activity.viewModelStore.put("identity-share", model)
        val person = model.identities.create().copy(nickname = "我的名片", tiles = listOf(
            ProfileTile(kind = ProfileTileKind.TEXT, title = "不应显示的磁贴")))
        model.identities.save(person)
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) { CloudSharingSetupHost(model) } }
        var error: String? = null
        val scope = CoroutineScope(Dispatchers.Main.immediate)
        compose.runOnIdle {
            scope.launch {
                model.events.collect { (_, result) ->
                    if (result is com.kigtts.plugin.api.PluginOutcome.Message) error = result.text
                }
            }
        }
        var launched: android.content.Intent? = null
        compose.runOnIdle { model.sharing.shareMedia(listOf(person.designId)) { launched = it } }
        compose.onNodeWithText("使用云端互传？").assertIsDisplayed()
        compose.onNodeWithText("使用系统分享").performClick()
        try {
            compose.waitUntil(12_000) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                launched != null || error != null
            }
        } finally { scope.cancel() }
        assertNull(error)
        assertEquals(android.content.Intent.ACTION_CHOOSER, launched!!.action)
        val send = requireNotNull(launched!!.getParcelableExtra(android.content.Intent.EXTRA_INTENT, android.content.Intent::class.java))
        assertEquals(android.content.Intent.ACTION_SEND, send.action)
        assertEquals("application/pdf", send.type)
        assertTrue(send.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val uri = requireNotNull(send.getParcelableExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java))
        assertEquals(uri, send.clipData?.getItemAt(0)?.uri)
        val file = java.io.File(app.cacheDir, "cards/" + uri.pathSegments.drop(1).joinToString("/"))
        assertEquals("%PDF-", file.inputStream().use { String(it.readNBytes(5), Charsets.US_ASCII) })
        compose.onNodeWithTag("face-share-identity").assertDoesNotExist()
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
