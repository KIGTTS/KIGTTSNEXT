package com.kigtts.next

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.state.*
import com.kigtts.next.theme.*
import com.kigtts.next.ui.home.QuickTextSelectorContent
import com.kigtts.plugin.api.QuickTextSelectionMode
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xhdpi")
class QuickTextSelectorUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun tabsKeepIconsAndSelectionWhileTheCardsFollowTheFinger() {
        var selected by mutableStateOf(emptySet<String>())
        val phrases = listOf(QuickPhrase("第一组", "常用", "first"), QuickPhrase("第二组", "生活", "second"))
        compose.setContent { KigttsTheme(ThemeSettings()) {
            QuickTextSelectorContent(phrases, listOf("常用", "生活"), mapOf("生活" to CategoryIcon.FOOD), null,
                selected, QuickTextSelectionMode.MULTIPLE, { selected = selected + it.id }, Modifier.fillMaxSize())
        } }
        compose.onNodeWithContentDescription("分组图标：生活", useUnmergedTree = true).assertExists()
        val tabsBefore = compose.onNodeWithTag("selector-tabs").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("selector-phrase/first").performClick().assertIsSelected()
        val before = compose.onNodeWithTag("selector-phrase/first").getUnclippedBoundsInRoot()
        val pager = compose.onNodeWithTag("selector-pages")
        pager.performTouchInput { down(Offset(width * .85f, height * .5f)); moveBy(Offset(-width * .4f, 0f), 250) }
        compose.waitForIdle()
        assertTrue(compose.onNodeWithTag("selector-phrase/first").getUnclippedBoundsInRoot().left < before.left)
        assertEquals(tabsBefore, compose.onNodeWithTag("selector-tabs").getUnclippedBoundsInRoot())
        pager.performTouchInput { moveBy(Offset(-width * .3f, 0f), 100); up() }
        compose.waitForIdle()
        compose.onNodeWithTag("selector-tab/生活").assertIsSelected()
        compose.onNodeWithTag("selector-phrase/second").performClick()
        assertEquals(setOf("first", "second"), selected)
        compose.onNodeWithTag("selector-tab/常用").performClick()
        compose.onNodeWithTag("selector-phrase/first").assertIsSelected()
        val bounds = compose.onNodeWithTag("selector-phrase/first").getUnclippedBoundsInRoot()
        assertEquals(88.dp, bounds.bottom - bounds.top)
    }

    @Test fun imageCardsActuallyLoadTheSameMediaThumbnailAsTheHomePanel() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val model = testAppModel(app)
        val file = File(app.cacheDir, "selector-image.png")
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.CYAN) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val media = model.mediaRepository.import(Uri.fromFile(file), "image/png")
        val phrase = QuickPhrase("图片", "常用", "photo", mediaId = media.id)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            QuickTextSelectorContent(listOf(phrase), listOf("常用"), emptyMap(), model.mediaRepository, emptySet(),
                QuickTextSelectionMode.SINGLE, {}, Modifier.fillMaxSize())
        } }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("media-thumbnail-" + media.id, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }
}
