package com.kigtts.next.identity

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.theme.*
import com.kigtts.next.ui.identity.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.testAppModel
import org.json.JSONObject
import com.kigtts.next.CardPrintCoordinatorTest
import kotlinx.coroutines.cancel

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED) @Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class ProfileTilesUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun verticalFavoritesExpandOneCardAndKeepTheNextCardBelowIt() {
        val model = testAppModel(ApplicationProvider.getApplicationContext<Application>())
        compose.activity.viewModelStore.put("favorite-test", model)
        val first = model.contactFavorites.save(JSONObject().put("identityId", "a").put("nickname", "朋友甲").put("bio", "摄影"))
        val second = model.contactFavorites.save(JSONObject().put("identityId", "b").put("nickname", "朋友乙").put("bio", "音乐"))
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) { ContactFavoritesScreen(model) } }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("favorite-card/${second.id}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("favorite-card/${second.id}").performClick()
        compose.onNodeWithTag("favorite-details/${second.id}").assertExists()
        compose.onNodeWithTag("contact-favorites-list").performScrollToKey(first.id)
        compose.onNodeWithTag("favorite-card/${first.id}").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("favorite-details/${second.id}").assertDoesNotExist()
        compose.onNodeWithTag("favorite-details/${first.id}").assertExists()
        val a = compose.onNodeWithTag("favorite-card/${first.id}").getUnclippedBoundsInRoot()
        val b = compose.onNodeWithTag("favorite-card/${second.id}").getUnclippedBoundsInRoot()
        assertTrue(a.top >= b.bottom)
        compose.onRoot().captureRoboImage("build/previews/contact-favorites.png")
    }
    @Test fun largeFavoritesDecodeOnlyVisibleCardsAndCanScrollToTheLastCard() {
        val model = testAppModel(ApplicationProvider.getApplicationContext<Application>())
        compose.activity.viewModelStore.put("favorite-lazy-test", model)
        val entries = (1..40).map { index -> model.contactFavorites.save(JSONObject().put("identityId", "friend-$index").put("nickname", "朋友 $index")) }
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) { ContactFavoritesScreen(model) } }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("favorite-card/${entries.last().id}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("favorite-card/${entries.first().id}").assertDoesNotExist()
        compose.onNodeWithTag("contact-favorites-list").performScrollToKey(entries.first().id)
        compose.onNodeWithTag("favorite-card/${entries.first().id}").assertIsDisplayed().performClick()
        compose.onNodeWithTag("favorite-details/${entries.first().id}").assertExists()
    }
    @Test fun headingUsesOneTextLineAndResizeHandleChangesGridSizeWithoutEditingContent() {
        var tiles by mutableStateOf(listOf(ProfileTile(id = "heading", kind = ProfileTileKind.TITLE, title = "关于我"),
            ProfileTile(id = "text", title = "你好", text = "摄影、音乐和写代码", columns = 2, rows = 1),
            ProfileTile(id = "link", kind = ProfileTileKind.LINK, title = "我的主页", text = "https://example.com", columns = 2, rows = 1)))
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
            Column(Modifier.width(360.dp).padding(12.dp)) {
                ProfileTags(listOf("Kigurumi", "开发", "摄影"))
                ProfileTileBoard(tiles, emptyList(), { null }, editing = true, change = { tiles = it })
            }
        } }
        val heading = compose.onNodeWithTag("profile-tile/heading").getUnclippedBoundsInRoot()
        val text = compose.onNodeWithTag("profile-tile/text").getUnclippedBoundsInRoot()
        assertEquals(36f, (heading.bottom - heading.top).value, .1f)
        assertTrue(text.bottom - text.top > heading.bottom - heading.top)
        compose.onNodeWithTag("tile-resize/heading").assertDoesNotExist()
        compose.onNodeWithTag("tile-resize/text").performTouchInput { down(center); moveBy(Offset(290f, 240f), 300); up() }
        compose.waitForIdle()
        assertTrue(tiles[1].columns > 2); assertTrue(tiles[1].rows > 1)
        assertEquals("摄影、音乐和写代码", tiles[1].text)
        compose.onRoot().captureRoboImage("build/previews/profile-tiles-edit.png")
    }
}
