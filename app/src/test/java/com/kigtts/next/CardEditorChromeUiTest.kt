package com.kigtts.next

import android.app.Application
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import com.kigtts.plugin.api.PolaroidCard
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w1000dp-h1000dp-xhdpi")
class CardEditorChromeUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun editorRouteAvoidsTheLiveLandscapePanelAndKeepsItsDraftAcrossRotation() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("appearance", 0).edit().clear().putString("mode", ThemeMode.LIGHT.name).commit()
        val model = testAppModel(app)
        val card = model.cards.create(PolaroidCard(title = "顶栏测试"))
        var width by mutableStateOf(393.dp); var height by mutableStateOf(820.dp)
        compose.setContent { Box(Modifier.size(width, height)) { KigttsApp(model) } }
        compose.runOnIdle { model.navigate(Destination.CardEditor(card.id)) }
        compose.onNodeWithTag("large-page-bar").assertIsDisplayed()
        compose.onNodeWithTag("page-subtitle").assertDoesNotExist()
        compose.onNodeWithTag("card-editor-tab/TEXT").performClick()
        compose.onNodeWithTag("card-title").performScrollTo().performTextReplacement("旋转保留草稿")
        compose.onNodeWithTag("card-editor-tab/CROP").performClick()
        compose.runOnIdle { width = 820.dp; height = 393.dp }
        compose.waitForIdle()
        compose.runOnIdle {
            WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
            val density = compose.activity.resources.displayMetrics.density
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, (24 * density).toInt(), 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, (24 * density).toInt())).build()
            val root = compose.activity.findViewById<ViewGroup>(android.R.id.content)
            ViewCompat.dispatchApplyWindowInsets(root.getChildAt(0), insets)
        }
        fun check() {
            compose.onNodeWithTag("large-page-bar").assertDoesNotExist()
            val bar = compose.onNodeWithTag("small-page-bar").getUnclippedBoundsInRoot()
            val panel = compose.onNodeWithTag("card-editor-panel").getUnclippedBoundsInRoot()
            val back = compose.onNodeWithTag("page-back").getUnclippedBoundsInRoot()
            val save = compose.onNodeWithTag("save-card").getUnclippedBoundsInRoot()
            // Paper uses a graphics-layer scale: layout size is deliberately retained.
            val paper = compose.onNodeWithTag("polaroid/${card.id}").fetchSemanticsNode().boundsInRoot
            val barPixels = compose.onNodeWithTag("small-page-bar").fetchSemanticsNode().boundsInRoot
            val panelPixels = compose.onNodeWithTag("card-editor-panel").fetchSemanticsNode().boundsInRoot
            assertTrue(back.top >= 24.dp)
            assertEquals("The panel occupies the safe height under transparent chrome", 24f, panel.top.value, 1f)
            assertTrue("Save must stay left of the moving tools: $save / $panel", save.right <= panel.left - 7.dp)
            assertTrue("Photo must clear the floating title: $paper / $bar", paper.top >= barPixels.bottom)
            assertTrue("Photo must clear the tools: $paper / $panel", paper.right < panelPixels.left)
        }
        check()
        val expandedSave = compose.onNodeWithTag("save-card").getUnclippedBoundsInRoot().left
        compose.onNodeWithTag("toggle-card-editor-panel").performClick()
        check()
        assertTrue(compose.onNodeWithTag("save-card").getUnclippedBoundsInRoot().left > expandedSave)
        compose.onNodeWithTag("toggle-card-editor-panel").performClick()
        compose.onNodeWithTag("card-editor-panel-header").performTouchInput {
            down(center); moveBy(Offset(-80f * density, 0f), delayMillis = 500)
        }
        compose.onRoot().captureRoboImage("build/previews/editor-floating-during-drag.png")
        check() // Assert avoidance before releasing the finger, not only after snapping.
        compose.onNodeWithTag("card-editor-panel-header").performTouchInput { advanceEventTime(300); up() }
        check()
        compose.onRoot().captureRoboImage("build/previews/editor-floating-landscape-header.png")
        compose.runOnIdle { width = 393.dp; height = 820.dp }
        compose.onNodeWithTag("large-page-bar").assertIsDisplayed()
        compose.onNodeWithTag("card-editor-tab/TEXT").performClick()
        compose.onNodeWithTag("card-title").performScrollTo().assertTextContains("旋转保留草稿")
        assertEquals("顶栏测试", model.cards.cards.value.single { it.id == card.id }.title)
        compose.onNodeWithTag("page-back").performClick()
        compose.onNodeWithText("保存修改？").assertIsDisplayed()
    }
}
