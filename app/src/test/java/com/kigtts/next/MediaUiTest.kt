package com.kigtts.next

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.media.*
import com.kigtts.next.state.*
import com.kigtts.next.ui.KigttsApp
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class MediaUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var model: AppViewModel
    private fun show(): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("experiments", "media-presentation", "quick-phrases", "appearance", "home-panel").forEach { app.getSharedPreferences(it,0).edit().clear().commit() }
        model = testAppModel(app); model.mediaRepository.entries.value.forEach { model.mediaRepository.remove(it.id) }
        compose.setContent { KigttsApp(model) }; return model
    }
    @After fun cleanup() { if (::model.isInitialized) { model.mediaRepository.entries.value.forEach { model.content.remove(it.id) } } }
    private fun image(): MediaEntry {
        val app = ApplicationProvider.getApplicationContext<Application>(); val file = File(app.cacheDir,"test-image.png")
        Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.CYAN); file.outputStream().use { compress(Bitmap.CompressFormat.PNG,100,it) }; recycle() }
        return model.mediaRepository.import(Uri.fromFile(file),"image/png")
    }
    @Test fun deletingDisplayedImageKeepsItsPixelsForExitAndThenRetiresIt() {
        show();compose.runOnIdle { model.text("恢复文字") };val entry=image()
        compose.runOnIdle { model.content.show(entry) }
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription(entry.name).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.mainClock.autoAdvance=false
        compose.runOnIdle { model.content.remove(entry.id) }
        compose.mainClock.advanceTimeByFrame();compose.mainClock.advanceTimeBy(48);compose.waitForIdle()
        compose.onNodeWithTag("media-exit/"+entry.id).assertExists().assertHasNoClickAction()
        val path="build/previews/media-exit-mid.png"
        compose.onRoot().captureRoboImage(path)
        val bounds=compose.onNodeWithTag("editor-area").getUnclippedBoundsInRoot()
        val density=compose.activity.resources.displayMetrics.density
        val bitmap=BitmapFactory.decodeFile(path)
        val color=bitmap.getPixel(((bounds.left.value+(bounds.right.value-bounds.left.value)*.3f)*density).toInt(),
            ((bounds.top.value+(bounds.bottom.value-bounds.top.value)*.25f)*density).toInt())
        bitmap.recycle();assertTrue(Color.red(color)<220&&Color.green(color)>230&&Color.blue(color)>230)
        compose.mainClock.advanceTimeBy(500);compose.waitForIdle()
        compose.onNodeWithTag("media-exit/"+entry.id).assertDoesNotExist()
        compose.onNodeWithText("恢复文字").assertIsDisplayed()
        assertNull(model.mediaRepository.get(entry.id))
        compose.mainClock.autoAdvance=true
    }
    @Test fun experimentalSettingsDefaultOffPersistsAndApplePlaybackDisappearsWhenDisabled() {
        show(); compose.runOnIdle { model.navigate(Destination.Settings); model.navigate(Destination.SettingsDetail(SettingsCategory.EXPERIMENTAL)) }
        compose.onNodeWithTag("apple-live-photos").assertIsOff().performClick().assertIsOn()
        compose.runOnIdle { assertTrue(testAppModel(ApplicationProvider.getApplicationContext()).state.value.appleLivePhotos) }
        compose.onRoot().captureRoboImage("build/previews/media-experimental.png")
        val entry = image(); val clip = File(ApplicationProvider.getApplicationContext<Application>().cacheDir,"live.mp4").apply { writeBytes(byteArrayOf(1,2,3)) }
        val pair = model.mediaRepository.pair(entry.id, Uri.fromFile(clip))
        compose.runOnIdle { model.content.show(pair); model.navigate(Destination.Home) }
        compose.onNodeWithTag("play-motion-photo").assertIsDisplayed()
        compose.runOnIdle { model.appleLivePhotos(false) }
        compose.onRoot().captureRoboImage("build/previews/media-apple-disabled.png")
        compose.onNodeWithTag("play-motion-photo").assertDoesNotExist(); compose.onNodeWithTag("media-image").assertIsDisplayed()
        compose.runOnIdle { assertFalse(testAppModel(ApplicationProvider.getApplicationContext()).state.value.appleLivePhotos) }
    }
    @Test fun phraseMediaSelectionPreservesDraftAndReturnsToFullscreenEditor() {
        show(); val entry = image()
        compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT); model.editQuickTexts() }
        compose.onNodeWithTag("add-phrase").performClick(); compose.onNodeWithTag("phrase-detail-text").performTextInput("图片条目")
        compose.onNodeWithTag("phrase-choose-media").performClick()
        compose.onNodeWithTag("phrase-detail-text").assertDoesNotExist()
        compose.onRoot().captureRoboImage("build/previews/media-library.png")
        compose.onNodeWithTag("media-select-" + entry.id).performClick()
        compose.onNodeWithTag("phrase-detail-text").assertTextContains("图片条目")
        assertEquals(compose.onNode(isDialog()).getUnclippedBoundsInRoot(), compose.onNodeWithTag("phrase-dialog-container").getUnclippedBoundsInRoot())
        compose.onNodeWithTag("save-phrase-details").performClick()
        compose.waitUntil(5000) { model.content.phraseDraft.value == null }
        compose.runOnIdle { val phrase = model.phrases.value.last(); assertEquals(entry.id, phrase.mediaId); model.finishQuickTextEditing(); model.content.send(phrase) }
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription(entry.name).fetchSemanticsNodes().isNotEmpty() }
        compose.onRoot().captureRoboImage("build/previews/media-selected.png")
        compose.onNodeWithTag("media-image").assertIsDisplayed(); compose.onNodeWithTag("speech-editor").assertDoesNotExist()
        compose.onNodeWithText("全屏").performClick(); compose.runOnIdle { assertTrue(model.state.value.forcedFullscreen) }
        compose.onRoot().captureRoboImage("build/previews/media-fullscreen.png")
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed(); model.text("文字") }
        compose.onNodeWithTag("media-image").assertDoesNotExist(); compose.onNodeWithText("文字").assertIsDisplayed()
    }
    @Test fun builtinEditorSavesTextAndConfirmsDirtyBackWithoutLosingDraft() {
        show(); compose.runOnIdle { model.content.editor() }
        compose.onNodeWithTag("content-editor-text").performTextReplacement("内置编辑器文本")
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("放弃未保存的修改？").assertIsDisplayed(); compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithTag("content-editor-text").assertTextContains("内置编辑器文本")
        compose.onNodeWithTag("save-content").performClick()
        compose.waitUntil(5000) { model.state.value.destination == Destination.Home }
        compose.runOnIdle { assertEquals("内置编辑器文本", model.state.value.editorText); assertNull(model.state.value.mediaId) }
    }
    @Test fun imageFitsWithContentBlurAndFillChoicePersistsThroughTextMenu() {
        show(); val entry = image()
        compose.runOnIdle { model.content.show(entry) }
        assertEquals(compose.onNodeWithTag("editor-area").getUnclippedBoundsInRoot(), compose.onNodeWithTag("media-image").getUnclippedBoundsInRoot())
        // The square foreground leaves bars in this tall viewport. They must show cyan content, not the surface color.
        compose.waitUntil(5000) {
            val bounds = compose.onNodeWithTag("media-image").getUnclippedBoundsInRoot(); val root = compose.onRoot().getUnclippedBoundsInRoot()
            compose.onRoot().captureRoboImage("build/previews/media-blur-check.png")
            val bitmap = BitmapFactory.decodeFile("build/previews/media-blur-check.png")
            val density = compose.activity.resources.displayMetrics.density
            val x = (((bounds.left.value + bounds.right.value)/2 - root.left.value) * density).toInt()
            val y = ((bounds.top.value - root.top.value) * density).toInt() + 8
            val color = bitmap.getPixel(x,y); bitmap.recycle()
            Color.green(color) > 230 && Color.blue(color) > 230 && Color.red(color) < 25
        }
        compose.onRoot().captureRoboImage("build/previews/media-fit-blur.png")
        compose.onNodeWithContentDescription("文本菜单").performClick()
        compose.onNodeWithTag("media-scale-fill").performClick()
        compose.waitUntil(5000) { model.mediaRepository.get(entry.id)?.scale == MediaScale.FILL }
        compose.runOnIdle { assertEquals(MediaScale.FILL, testAppModel(ApplicationProvider.getApplicationContext()).mediaRepository.get(entry.id)?.scale) }
        compose.onRoot().captureRoboImage("build/previews/media-fill.png")
        compose.onNodeWithContentDescription("文本菜单").performClick(); compose.onNodeWithTag("media-scale-fit").performClick()
        compose.waitUntil(5000) { model.mediaRepository.get(entry.id)?.scale == MediaScale.FIT }
    }
}
