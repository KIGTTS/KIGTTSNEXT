package com.kigtts.next.sharing

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kigtts.next.theme.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class FaceReceiveBannerTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun pendingOfferAppearsAtTheTopAndNeverAcceptsWithoutATap() {
        var offer by mutableStateOf<FaceOffer?>(FaceOffer(faceToken(), faceToken(), JSONObject().put("nickname", "朋友"), emptyList()))
        var accepted = 0
        compose.setContent { KigttsTheme(ThemeSettings()) {
            FaceReceiveBanner(offer, { _, auto -> assertFalse(auto); accepted++; offer = null }, { offer = null })
        } }
        compose.onNodeWithText("朋友 想与你分享").assertIsDisplayed()
        val bounds = compose.onNodeWithTag("face-incoming-banner").getUnclippedBoundsInRoot()
        assertTrue(bounds.top.value < 100f); assertEquals(0, accepted)
        compose.onNodeWithText("接收").performClick()
        compose.mainClock.advanceTimeBy(500); compose.waitForIdle()
        assertEquals(1, accepted)
        compose.onNodeWithTag("face-incoming-banner").assertDoesNotExist()
    }
}
