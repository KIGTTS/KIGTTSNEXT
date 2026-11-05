package com.kigtts.next

import android.app.Application
import android.graphics.BitmapFactory
import android.view.ViewGroup
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.state.*
import com.kigtts.next.theme.*
import com.kigtts.next.ui.KigttsApp
import com.kigtts.next.ui.cards.CardPageScaffold
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class CardPageChromeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun show(): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("appearance", 0).edit().clear().putString("mode", ThemeMode.LIGHT.name).commit()
        return testAppModel(app).also { model -> compose.setContent { KigttsApp(model) }; compose.waitForIdle() }
    }
    private fun systemInsets() = compose.runOnIdle {
        WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
        val density = compose.activity.resources.displayMetrics.density
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, (24 * density).toInt(), 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, (24 * density).toInt())).build()
        val root = compose.activity.findViewById<ViewGroup>(android.R.id.content)
        ViewCompat.dispatchApplyWindowInsets(root.getChildAt(0), insets)
    }

    @Test fun portraitSideAndDeeperEntryUseTheDeviceAlbum() {
        val model = show()
        fun checkAlbum() {
            compose.onNodeWithTag("device-album").assertIsDisplayed()
            compose.onNodeWithTag("album-title").assertIsDisplayed()
            compose.onNodeWithContentDescription("系统相册").assertIsDisplayed()
        }
        compose.runOnIdle { model.openSidePage(SidePage.CARDS) }
        checkAlbum()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.runOnIdle { model.navigate(Destination.Cards) }
        checkAlbum()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.runOnIdle { assertEquals(Destination.Home, model.state.value.destination) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "zh-rCN-w852dp-h393dp-land-xxhdpi")
    fun theDeviceAlbumStaysUsableInLandscapeAndRetainsPredictiveReturn() {
        val model = show(); systemInsets()
        fun checkAlbum() {
            compose.onNodeWithTag("device-album").assertIsDisplayed()
            val title = compose.onNodeWithTag("album-title").getUnclippedBoundsInRoot()
            val body = compose.onNodeWithTag("device-album").getUnclippedBoundsInRoot()
            assertTrue(title.top.value >= 24f)
            assertTrue(body.top >= title.bottom)
        }
        compose.runOnIdle { model.openSidePage(SidePage.CARDS) }
        checkAlbum()
        compose.onNodeWithContentDescription("返回").performClick()
        val parent = Destination.PluginPage("tools.cloud-agent", "chat")
        compose.runOnIdle { model.navigate(parent); model.navigate(Destination.Cards) }
        checkAlbum()
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 100f, .5f, BackEventCompat.EDGE_LEFT))
        }
        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(Destination.Cards, model.state.value.destination) }
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(parent, model.state.value.destination) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "zh-rCN-w852dp-h393dp-land-xxhdpi")
    fun theFloatingLandscapeToolbarRevealsTheBodyPixelsAndConsumesNoBrowsingHeight() {
        var returns = 0
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
            Box(Modifier.size(700.dp, 300.dp)) { CardPageScaffold("拍立得", { returns++ }) {
                Box(Modifier.fillMaxSize().background(Color.Blue).testTag("floating-card-body"))
            } }
        } }
        val body = compose.onNodeWithTag("floating-card-body").getUnclippedBoundsInRoot()
        val bar = compose.onNodeWithTag("small-page-bar").getUnclippedBoundsInRoot()
        assertEquals(bar.top.value, body.top.value, .5f)
        assertTrue(body.bottom > bar.bottom)
        val output = File("build/previews/polaroid-floating-landscape-header.png").apply { parentFile.mkdirs() }
        compose.onNodeWithTag("small-page-bar").captureRoboImage(output.absolutePath)
        val image = requireNotNull(BitmapFactory.decodeFile(output.absolutePath))
        try {
            listOf(2 to 2, 2 to (image.height / 2), (image.width - 3) to 2).forEach { (x, y) ->
                val pixel = image.getPixel(x, y)
                assertTrue("Transparent chrome must reveal the actual blue body pixels", android.graphics.Color.blue(pixel) >= 248 &&
                    android.graphics.Color.red(pixel) <= 8 && android.graphics.Color.green(pixel) <= 8)
            }
        } finally { image.recycle() }
        compose.onNodeWithTag("page-back").performClick()
        assertEquals(1, returns)
    }

    @Test
    @Config(sdk = [35], qualifiers = "zh-rCN-w852dp-h393dp-land-xxhdpi")
    fun resizingTheWindowOverridesDeviceOrientationWithoutRecreatingTheBody() {
        var width by mutableStateOf(700.dp)
        var height by mutableStateOf(300.dp)
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
            Box(Modifier.size(width, height)) { CardPageScaffold("拍立得", {}) {
                var count by remember { mutableIntStateOf(0) }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    TextButton({ count++ }, Modifier.testTag("body-counter")) { Text("$count") }
                }
            } }
        } }
        compose.onNodeWithTag("small-page-bar").assertIsDisplayed()
        compose.onNodeWithTag("body-counter").performClick()
        compose.runOnIdle { width = 260.dp; height = 350.dp }
        compose.onNodeWithTag("large-page-bar").assertIsDisplayed()
        compose.onNodeWithTag("small-page-bar").assertDoesNotExist()
        compose.onNodeWithTag("body-counter").assertTextEquals("1")
        compose.runOnIdle { width = 700.dp; height = 300.dp }
        compose.onNodeWithTag("small-page-bar").assertIsDisplayed()
        compose.onNodeWithTag("body-counter").assertTextEquals("1")
    }
}
