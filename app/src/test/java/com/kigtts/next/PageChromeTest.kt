package com.kigtts.next

import android.app.Application
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.*
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.state.*
import com.kigtts.next.ui.KigttsApp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk=[35],qualifiers="zh-rCN-w393dp-h852dp-xxhdpi")
class PageChromeTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun show():AppViewModel {
        val app=ApplicationProvider.getApplicationContext<Application>()
        listOf("appearance","ui-debug","home-panel","quick-phrases").forEach { app.getSharedPreferences(it,0).edit().clear().commit() }
        return testAppModel(app).also { model -> compose.setContent { KigttsApp(model) };compose.waitForIdle() }
    }
    private fun systemInsets() {
        compose.runOnIdle {
            WindowCompat.setDecorFitsSystemWindows(compose.activity.window,false)
            val density=compose.activity.resources.displayMetrics.density
            val insets=WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(),Insets.of(0,(24*density).toInt(),0,0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(),Insets.of(0,0,0,(24*density).toInt())).build()
            val root=compose.activity.findViewById<ViewGroup>(android.R.id.content)
            ViewCompat.dispatchApplyWindowInsets(root.getChildAt(0),insets)
        }
    }
    @Test fun largeBarCoversStatusAreaCollapsesWithScrollAndRetainsItsPoseAfterReturn() {
        val model=show();systemInsets()
        compose.runOnIdle { model.navigate(Destination.Settings);model.navigate(Destination.SettingsDetail(SettingsCategory.APPEARANCE)) }
        val layer=compose.onNodeWithTag("page-layer/2/settings/APPEARANCE").getUnclippedBoundsInRoot()
        val bar=compose.onNodeWithTag("large-page-bar")
        val expanded=bar.getUnclippedBoundsInRoot()
        assertEquals(0f,layer.top.value,.5f);assertEquals(layer.top.value,expanded.top.value,.5f)
        val back=compose.onNodeWithContentDescription("返回上一页").getUnclippedBoundsInRoot()
        assertTrue(back.top.value>=24f)
        compose.onNodeWithContentDescription("打开设置").assertDoesNotExist()
        compose.onRoot().captureRoboImage("build/previews/immersive-large-bar-expanded.png")
        compose.onNode(SemanticsMatcher("vertical-scroll") { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) } and
            hasAnyAncestor(hasTestTag("navigation-secondary"))).performTouchInput { swipeUp() }
        compose.waitForIdle()
        val collapsed=bar.getUnclippedBoundsInRoot()
        assertTrue(expanded.bottom-expanded.top>collapsed.bottom-collapsed.top+30.dp)
        compose.onRoot().captureRoboImage("build/previews/immersive-large-bar-collapsed.png")
        compose.runOnIdle { model.navigate(Destination.Plugins) }
        compose.onNodeWithContentDescription("安装插件").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回上一页").performClick()
        assertEquals(collapsed,bar.getUnclippedBoundsInRoot())
    }
    @Test fun actionsFollowEachPageInsteadOfKeepingTheDrawer() {
        val model=show()
        compose.runOnIdle { model.content.picker() }
        compose.onNodeWithTag("import-media").assertIsDisplayed()
        compose.onNodeWithContentDescription("打开设置").assertDoesNotExist()
        compose.onNodeWithContentDescription("更多页面操作").performClick()
        compose.onNodeWithTag("create-html").performClick()
        compose.waitUntil(5000) { model.state.value.destination is Destination.HtmlFiles }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("html-file/index.html").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("html-import").assertIsDisplayed()
        compose.onNodeWithTag("html-file/index.html").performClick()
        compose.waitUntil(5000) { model.state.value.destination is Destination.ContentEditor }
        compose.waitForIdle()
        compose.onNodeWithTag("save-content").assertIsDisplayed()
        compose.onNodeWithTag("import-media").assertDoesNotExist()
        compose.onNodeWithContentDescription("查找替换").assertIsDisplayed()
    }
    @Test fun sketchUsesCompactPinnedBarAndKeepsItsShareAction() {
        val model=show();systemInsets()
        compose.runOnIdle { model.openSidePage(SidePage.SKETCH) }
        compose.onNodeWithTag("large-page-bar").assertDoesNotExist()
        val bar=compose.onNodeWithTag("small-page-bar").getUnclippedBoundsInRoot()
        assertEquals(88f,bar.bottom.value-bar.top.value,.5f)
        compose.onNodeWithContentDescription("分享画板").assertIsDisplayed()
        compose.onNodeWithTag("sketch-canvas").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/previews/immersive-sketch-small-bar.png")
    }
}
