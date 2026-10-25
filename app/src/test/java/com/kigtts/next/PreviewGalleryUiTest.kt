package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("竖向预览").fetchSemanticsNodes().isNotEmpty() }
        val wide = compose.onNodeWithTag("plugin-preview/0").fetchSemanticsNode().boundsInRoot
        val tall = compose.onNodeWithTag("plugin-preview/1").fetchSemanticsNode().boundsInRoot
        assertEquals(5f, wide.width / wide.height, .03f)
        assertEquals(.2f, tall.width / tall.height, .01f)
        assertTrue(wide.height < tall.height)
        compose.onNodeWithTag("plugin-preview/1").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/previews/store-arbitrary-previews.png")
        compose.onNodeWithContentDescription("横向预览").performClick()
        compose.onNodeWithContentDescription("关闭").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("关闭").assertDoesNotExist()
    }

    @Test fun mixedGalleryAndFullscreenBrowseAllPreviewsAndKeepTheViewedPosition() {
        val root = ApplicationProvider.getApplicationContext<Application>().cacheDir
        val images = (0..7).map { index ->
            val width = if (index % 2 == 0) 1200 else 300
            val height = if (index % 2 == 0) 300 else 1200
            val file = File(root, "carousel-$index.svg").apply { writeText("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $width $height"><rect width="$width" height="$height" fill="#71aaa5"/></svg>""") }
            PageImage(PluginResource(file.name), "预览 ${index + 1}", width, height)
        }
        compose.setContent { KigttsTheme(ThemeSettings()) { Surface(Modifier.width(360.dp)) {
            PluginGallery(PageBlock.Gallery(images)) { File(root, it.path) }
        } } }
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("预览 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onRoot().captureRoboImage("build/previews/store-mixed-gallery-mobile.png")
        compose.onNodeWithTag("plugin-preview-position").assertTextEquals("1 / 8")
        compose.onNodeWithTag("plugin-preview-carousel").performTouchInput {
            swipeLeft(startX = width * .9f, endX = width * .1f, durationMillis = 700)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("plugin-preview-position").assertTextEquals("2 / 8")
        compose.onNodeWithTag("plugin-preview/1").performClick()
        compose.onNodeWithTag("plugin-preview-viewer-position").assertTextEquals("2 / 8")
        repeat(6) { compose.onNodeWithTag("plugin-preview-viewer").performTouchInput { swipeLeft() }; compose.waitForIdle() }
        compose.onNodeWithTag("plugin-preview-viewer-position").assertTextEquals("8 / 8")
        compose.onNodeWithTag("plugin-preview-viewer").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithTag("plugin-preview-viewer-position").assertTextEquals("8 / 8")
        compose.onNodeWithContentDescription("关闭").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("plugin-preview-position").assertTextEquals("8 / 8")
        compose.onNodeWithTag("plugin-preview-viewer").assertDoesNotExist()
    }

    @Test fun bitmapAndVectorWithoutDimensionsUseTheirActualProportions() {
        val root = ApplicationProvider.getApplicationContext<Application>().cacheDir
        val bitmap = android.graphics.Bitmap.createBitmap(1600, 900, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(113, 170, 165))
        val file = File(root, "no-size.png").apply { outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        bitmap.recycle()
        val vector = File(root, "no-size.svg").apply { writeText("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 320 640"><rect width="320" height="640" fill="#9a81be"/></svg>""") }
        val images = listOf(PageImage(PluginResource(file.name), "横图"), PageImage(PluginResource(vector.name), "竖图"))
        compose.setContent { KigttsTheme(ThemeSettings()) { Surface(Modifier.width(700.dp)) {
            PluginGallery(PageBlock.Gallery(images)) { File(root, it.path) }
        } } }
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("横图").fetchSemanticsNodes().isNotEmpty() && compose.onAllNodesWithContentDescription("竖图").fetchSemanticsNodes().isNotEmpty() }
        val wide = compose.onNodeWithTag("plugin-preview/0").fetchSemanticsNode().boundsInRoot
        val tall = compose.onNodeWithTag("plugin-preview/1").fetchSemanticsNode().boundsInRoot
        assertEquals(1600f / 900, wide.width / wide.height, .01f)
        assertEquals(.5f, tall.width / tall.height, .01f)
        compose.onRoot().captureRoboImage("build/previews/store-mixed-gallery-tablet.png")
    }

    @Test fun onePreviewOpensWithoutPagingControls() {
        val root = ApplicationProvider.getApplicationContext<Application>().cacheDir
        val file = File(root, "single.svg").apply { writeText("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 100"><rect width="10" height="100"/></svg>""") }
        compose.setContent { KigttsTheme(ThemeSettings()) { Surface(Modifier.width(320.dp)) {
            PluginGallery(PageBlock.Gallery(listOf(PageImage(PluginResource(file.name), "单张预览", 10, 100)))) { File(root, it.path) }
        } } }
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("单张预览").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("plugin-preview-position").assertDoesNotExist()
        compose.onNodeWithTag("plugin-preview/0").performClick()
        compose.onNodeWithTag("plugin-preview-viewer-position").assertTextEquals("1 / 1")
        compose.onNodeWithContentDescription("关闭").performClick()
        compose.onNodeWithTag("plugin-preview-viewer").assertDoesNotExist()
    }
}
