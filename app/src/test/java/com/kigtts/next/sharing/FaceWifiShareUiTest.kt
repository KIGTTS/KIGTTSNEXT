package com.kigtts.next.sharing

import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.kigtts.next.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [29, 35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class FaceWifiShareUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun code(description: String, expected: String, name: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(description).assertIsDisplayed()
        val path = "build/previews/face-wifi-$name-api${android.os.Build.VERSION.SDK_INT}.png"
        compose.onNode(hasTestTag("face-qr-frame") and hasAnyDescendant(hasContentDescription(description)))
            .captureRoboImage(path)
        val bitmap = BitmapFactory.decodeFile(path)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels))))
        assertEquals("Rounded frame must preserve a scannable code", expected, decoded.text)
        bitmap.recycle()
    }

    @Test fun swipeAndStepButtonsSeparateTheTwoScannableCodes() {
        val hotspot = HotspotDetails("KIGTTS;临时网络", "test:password")
        val url = "http://192.168.49.1:40001/share/example"
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
            FaceShareSheet("Wi-Fi 互传", {}, 580.dp, back = {}, finish = {}) { FaceWifiShareContent(hotspot, url, false, {}) }
        } }
        code("连接热点二维码", hotspot.qr(), "network")
        compose.onNodeWithTag("face-share").captureRoboImage("build/previews/face-wifi-network-page-api${android.os.Build.VERSION.SDK_INT}.png")
        compose.onNodeWithTag("face-wifi-pager").performTouchInput { swipeLeft() }
        code("领取照片二维码", url, "collection")
        compose.onNodeWithText("2 / 2").assertIsDisplayed()
        compose.onNodeWithTag("face-wifi-pager").performTouchInput { swipeRight() }
        compose.onNodeWithText("1 / 2").assertIsDisplayed()
        compose.onNodeWithText("已连接，下一步").performClick()
        compose.onNodeWithText("2 / 2").assertIsDisplayed()
        compose.onNodeWithTag("face-share").captureRoboImage("build/previews/face-wifi-collection-page-api${android.os.Build.VERSION.SDK_INT}.png")
        compose.onNodeWithText("返回连接网络").performClick()
        compose.onNodeWithText("1 / 2").assertIsDisplayed()
    }

    @Test fun sameWifiSkipsHotspotAndLateUrlDoesNotResetThePage() {
        var starts = 0
        var url by mutableStateOf<String?>(null)
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.DARK)) {
            FaceShareSheet("Wi-Fi 互传", {}, 580.dp, back = {}, finish = {}) { FaceWifiShareContent(null, url, true, { starts++ }) }
        } }
        compose.onNodeWithText("创建临时热点").performClick()
        assertEquals(1, starts)
        compose.onNodeWithText("已连接，下一步").performClick()
        compose.onNodeWithText("等待 Wi-Fi 连接").assertIsDisplayed()
        compose.runOnIdle { url = "http://192.168.1.5:40002/share/card" }
        code("领取名片二维码", requireNotNull(url), "personal-dark")
        compose.onNodeWithText("2 / 2").assertIsDisplayed()
        assertEquals(1, starts)
    }
}
