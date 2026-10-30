package com.kigtts.next.identity

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.theme.*
import com.kigtts.next.ui.identity.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED) @Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class ProfileImagePreviewUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun gif() = File(compose.activity.cacheDir, "preview.gif").apply {
        writeBytes(requireNotNull(ProfileImagePreviewUiTest::class.java.classLoader!!.getResourceAsStream("fixtures/profile/animated.gif")).use { it.readBytes() })
    }
    @Test fun imageTilesOpenAFullImagePreviewWhileEditingStillOpensTheEditor() {
        val file = gif(); var editing by mutableStateOf(true); var edits = 0
        val tile = ProfileTile(id = "photo", kind = ProfileTileKind.IMAGE, title = "照片", image = "image", columns = 4, rows = 2)
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
            Box(Modifier.width(360.dp)) { ProfileTileBoard(listOf(tile), emptyList(), { file }, editing = editing, edit = { edits++ }) }
        } }
        compose.onNodeWithTag("profile-tile/photo").performTouchInput { click() }; assertEquals(1, edits)
        compose.onNodeWithTag("profile-image-preview").assertDoesNotExist()
        compose.runOnIdle { editing = false }
        compose.onNodeWithTag("profile-tile/photo").performTouchInput { click() }
        compose.onNodeWithTag("profile-image-preview").assertIsDisplayed()
        compose.onNodeWithText("保存到相册").assertIsDisplayed()
        compose.onNodeWithContentDescription("关闭图片预览").performClick()
        compose.mainClock.advanceTimeBy(300); compose.waitForIdle()
        compose.onNodeWithTag("profile-image-preview").assertDoesNotExist()
    }
    @Test fun saveUsesOriginalGifAndConfirmsSuccessWithoutClosingPreview() {
        val file = gif(); var saved: File? = null
        compose.setContent { KigttsTheme(ThemeSettings()) {
            ProfileImagePreview(file, "照片", {}, save = { _, image -> saved = image })
        } }
        compose.onNodeWithTag("save-profile-image").performClick()
        compose.onNodeWithText("已保存到系统相册").assertIsDisplayed()
        assertEquals(file, saved)
        compose.onNodeWithTag("profile-image-preview").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/previews/profile-image-preview.png")
    }
}
