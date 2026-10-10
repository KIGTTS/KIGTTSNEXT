package com.kigtts.next

import android.app.Application
import android.graphics.*
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.media.*
import com.kigtts.next.state.*
import com.kigtts.next.theme.ThemeMode
import com.kigtts.next.ui.KigttsApp
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class MediaThumbnailUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var model: AppViewModel
    private fun show() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("appearance", "home-panel", "quick-phrases", "media-presentation").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        model = testAppModel(app); model.mediaRepository.entries.value.forEach { model.mediaRepository.remove(it.id) }
        compose.setContent { KigttsApp(model) }
    }
    @After fun cleanup() { if (::model.isInitialized) model.mediaRepository.entries.value.forEach { model.content.remove(it.id) } }
    private fun illustration(): MediaEntry {
        val app = ApplicationProvider.getApplicationContext<Application>(); val file = File(app.cacheDir, "sticker-fixture.png")
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG); val canvas = Canvas(bitmap)
        paint.color = Color.rgb(240, 181, 72); canvas.drawCircle(180f, 120f, 68f, paint)
        paint.color = Color.rgb(31, 59, 62); canvas.drawCircle(156f, 108f, 10f, paint); canvas.drawCircle(204f, 108f, 10f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f; canvas.drawArc(RectF(155f, 113f, 207f, 154f), 10f, 160f, false, paint)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        return model.mediaRepository.import(Uri.fromFile(file), "image/png")
    }
    private fun scene(): MediaEntry {
        val app = ApplicationProvider.getApplicationContext<Application>(); val file = File(app.cacheDir, "scene-fixture.png")
        val bitmap = Bitmap.createBitmap(320, 192, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 320f, 192f, Color.rgb(24, 148, 195), Color.rgb(72, 159, 112), Shader.TileMode.CLAMP)
        canvas.drawPaint(paint); paint.shader = null; paint.color = Color.rgb(241, 187, 65); canvas.drawCircle(235f, 75f, 43f, paint)
        paint.color = Color.rgb(16, 81, 91); canvas.drawOval(RectF(108f, 116f, 358f, 258f), paint)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        return model.mediaRepository.import(Uri.fromFile(file), "image/png")
    }
    @Test fun cardsHaveSharedPreviewInBothThemesAndManualFocusPersists() {
        show(); val entry = illustration(); val scene = scene()
        compose.runOnIdle { model.quickTexts.addCategory("媒体"); model.category("媒体")
            model.quickTexts.add("你好", "媒体", "挥挥手", entry.id); model.quickTexts.add("路上", "媒体", "一段风景", scene.id); model.openPanel(HomePanel.QUICK_TEXT) }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("media-thumbnail-" + entry.id, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("media-thumbnail-" + scene.id, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { model.theme(model.state.value.theme.copy(mode = ThemeMode.LIGHT)) }
        compose.onRoot().captureRoboImage("build/previews/media-thumbnail-light.png")
        compose.runOnIdle { model.theme(model.state.value.theme.copy(mode = ThemeMode.DARK)) }
        compose.onRoot().captureRoboImage("build/previews/media-thumbnail-dark.png")
        compose.onNodeWithContentDescription("编辑快捷文本").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("media-thumbnail-" + entry.id, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("editable-phrases").captureRoboImage("build/previews/media-thumbnail-edit.png")
        compose.runOnIdle { model.finishQuickTextEditing(); model.content.picker() }
        compose.onNodeWithTag("manage-media-" + entry.id).performClick()
        compose.onNodeWithTag("edit-thumbnail-framing").performClick()
        compose.onNodeWithContentDescription("缩略图取景：手动取景").performClick()
        compose.onNodeWithTag("thumbnail-focus-source").performTouchInput { click(Offset(width * .75f, height * .5f)) }
        compose.onNode(isDialog()).captureRoboImage("build/previews/media-thumbnail-framing.png")
        compose.onNodeWithTag("save-thumbnail-framing").performClick()
        compose.waitUntil(5000) { model.mediaRepository.get(entry.id)?.thumbnailMode == ThumbnailMode.FOCUS }
        assertTrue(model.mediaRepository.get(entry.id)!!.focusX > .65)
    }
    @Test fun webOccupiesEntireCardWithMeasuredSafeAreaAndRefreshesItInFullscreen() {
        show(); val entry = model.mediaRepository.html("安全区", "<html><body><h1>内容</h1></body></html>")
        compose.runOnIdle { model.content.show(entry) }
        compose.onNodeWithTag("media-web").assertIsDisplayed()
        fun webViews(view: View): List<WebView> = if (view is WebView) listOf(view) else if (view is ViewGroup)
            (0 until view.childCount).flatMap { webViews(view.getChildAt(it)) } else emptyList()
        lateinit var web: WebView
        compose.runOnIdle {
            web = webViews(compose.activity.window.decorView).single()
            // Robolectric has no Chromium viewport; the actual Compose measurement drives CSS units.
            shadowOf(web).webViewClient.onPageFinished(web, web.url.orEmpty())
        }
        compose.waitForIdle()
        val card = compose.onNodeWithTag("editor-area").getUnclippedBoundsInRoot()
        val bounds = compose.onNodeWithTag("media-web").getUnclippedBoundsInRoot()
        assertEquals(card, bounds)
        compose.runOnIdle {
            val script = shadowOf(web).lastEvaluatedJavascript
            assertNotNull("Measured WebView ${web.width}x${web.height}, callback ${shadowOf(web).webViewClient}", script)
            assertTrue(script.contains("--kigtts-safe-area-top")); assertTrue(script.contains("kigtts-safe-area-change"))
            assertFalse(script.contains("top:0*ratio")); assertFalse(script.contains("bottom:0*ratio"))
            File("build/previews/web-safe-area-script.js").apply { parentFile!!.mkdirs(); writeText(script) }
            model.enterSubtitleFullscreen()
        }
        compose.waitForIdle()
        assertEquals(compose.onNodeWithTag("editor-area").getUnclippedBoundsInRoot(), compose.onNodeWithTag("media-web").getUnclippedBoundsInRoot())
        compose.runOnIdle { assertEquals(1, webViews(compose.activity.window.decorView).size) }
    }
}
