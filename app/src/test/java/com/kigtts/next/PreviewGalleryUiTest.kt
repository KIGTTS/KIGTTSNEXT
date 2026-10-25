package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.theme.*
import com.kigtts.next.ui.plugins.PluginGallery
import com.kigtts.next.media.UiIcons
import com.kigtts.plugin.api.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], qualifiers = "zh-rCN-w700dp-h900dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class PreviewGalleryUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun nonSquareVectorPreviewsKeepAspectAndOpenLarge() {
        val root = ApplicationProvider.getApplicationContext<Application>().cacheDir
        val landscape = File(root, "wide.svg").apply { writeText("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 200"><rect width="1000" height="200" fill="#71aaa5"/></svg>""") }
        val portrait = File(root, "tall.svg").apply { writeText("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 1000"><rect width="200" height="1000" fill="#9a81be"/></svg>""") }
        assertEquals(5f, UiIcons.renderPreviewSvg(landscape.readText(), 1200).let { it.width.toFloat() / it.height }, .01f)
        val images = listOf(PageImage(PluginResource(landscape.name), "横向预览", 1000, 200), PageImage(PluginResource(portrait.name), "竖向预览", 200, 1000))
        compose.setContent { KigttsTheme(ThemeSettings()) { Surface(Modifier.fillMaxSize()) {
            PluginGallery(PageBlock.Gallery(images)) { File(root, it.path) }
        } } }
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("横向预览").fetchSemanticsNodes().isNotEmpty() }
        compose.onRoot().captureRoboImage("build/previews/store-arbitrary-previews.png")
        compose.onNodeWithContentDescription("横向预览").performClick()
        compose.onNodeWithText("关闭").assertIsDisplayed().performClick()
        compose.onNodeWithText("关闭").assertDoesNotExist()
    }
}
