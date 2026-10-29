package com.kigtts.next.identity

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.theme.*
import com.kigtts.next.ui.identity.ProfileTileBoard
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED) @Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class ProfileTitleOnlyTileUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun blankBodyTitlesStayCenteredAndLargerAcrossTileSizesAndEditMode() {
        var editing by mutableStateOf(false)
        val tiles = listOf(ProfileTile(id = "small", title = "你好", columns = 1),
            ProfileTile(id = "wide", title = "你所热爱的，就是你的生活", text = " \n ", columns = 3),
            ProfileTile(id = "tall", title = "很高兴认识你", rows = 2),
            ProfileTile(id = "heading", kind = ProfileTileKind.TITLE, title = "整行标题"))
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
            Surface { Box(Modifier.width(360.dp)) { ProfileTileBoard(tiles, emptyList(), { null }, editing = editing) } }
        } }
        fun check() = tiles.filter { it.kind == ProfileTileKind.TEXT }.forEach { tile ->
            val text = compose.onNodeWithText(tile.title)
            val bounds = compose.onNodeWithTag("profile-tile/${tile.id}").fetchSemanticsNode().boundsInRoot
            val textBounds = text.fetchSemanticsNode().boundsInRoot
            assertEquals("文字应垂直居中", bounds.center.y, textBounds.center.y, 1f)
            val results = mutableListOf<TextLayoutResult>()
            text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            assertEquals(TextAlign.Center, results.single().layoutInput.style.textAlign)
            assertEquals(22.sp, results.single().layoutInput.style.fontSize)
            assertFalse("${tile.id}: ${results.single().size}, lines=${results.single().lineCount}, editing=$editing", results.single().hasVisualOverflow)
        }
        check()
        compose.onRoot().captureRoboImage("build/previews/profile-title-only-text.png")
        compose.runOnIdle { editing = true }; check()
        val heading = compose.onNodeWithText("整行标题").fetchSemanticsNode().boundsInRoot
        assertTrue("整行标题仍靠左", heading.left < 120f)
    }
}
