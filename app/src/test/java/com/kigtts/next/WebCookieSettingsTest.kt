package com.kigtts.next

import android.webkit.CookieManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kigtts.next.i18n.uiCopy
import com.kigtts.next.media.WebCookies
import com.kigtts.next.ui.settings.WebSettingsScreen
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35])
class WebCookieSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun nativeStoreSharesJsAndNetworkCookiesAndSettingsClearRequiresConfirmation() {
        val manager = CookieManager.getInstance(); manager.removeAllCookie(); WebCookies.enable()
        WebCookies.receive("https://site.example/", listOf("session=network; Path=/", "preferences=dark; Path=/"))
        manager.setCookie("https://site.example/", "javascript=shared; Path=/")
        assertTrue(WebCookies.header("https://site.example/page")!!.contains("session=network"))
        assertTrue(WebCookies.header("https://site.example/page")!!.contains("javascript=shared"))
        assertNull(WebCookies.header("https://other.example/"))
        compose.setContent { MaterialTheme { WebSettingsScreen() } }
        compose.onNodeWithTag("clear-web-cookies").performClick()
        compose.onNodeWithTag("cancel-clear-web-cookies").performClick()
        assertTrue(manager.hasCookies())
        compose.onNodeWithTag("clear-web-cookies").performClick()
        compose.onNodeWithTag("confirm-clear-web-cookies").performClick()
        compose.waitUntil(5000) { !manager.hasCookies() }
        assertNull(WebCookies.header("https://site.example/"))
        compose.onNodeWithText(compose.activity.uiCopy("web.cookies_cleared", locale = "zh-CN")).assertExists()
    }
}
