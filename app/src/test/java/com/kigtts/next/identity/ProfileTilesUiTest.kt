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
import com.kigtts.next.i18n.LocalUiLocale
import com.kigtts.next.i18n.uiCopy
import com.kigtts.next.state.Destination
import org.json.JSONObject
import com.kigtts.next.CardPrintCoordinatorTest
import kotlinx.coroutines.cancel

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED) @Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class ProfileTilesUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun favoritesOpenDetailAndBackReturnsToTheList() {
        val model = testAppModel(ApplicationProvider.getApplicationContext<Application>())
        compose.activity.viewModelStore.put("favorite-test", model)
        val first = model.contactFavorites.save(JSONObject().put("identityId", "a").put("nickname", "朋友甲").put("bio", "摄影"))
        val second = model.contactFavorites.save(JSONObject().put("identityId", "b").put("nickname", "朋友乙").put("bio", "音乐"))
        model.navigate(Destination.ContactFavorites)
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
            val state by model.state.collectAsState()
            val detail = state.destination as? Destination.ContactFavoriteDetail
            if (detail == null) ContactFavoritesScreen(model) else ContactFavoriteDetailScreen(model, detail.id)
        } }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("favorite-card/${second.id}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("favorite-card/${second.id}").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("favorite-details/${second.id}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("contact-favorites-list").assertDoesNotExist()
        compose.runOnIdle { model.navigateBack() }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("favorite-card/${second.id}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("contact-favorites-list").performScrollToKey(first.id)
        compose.onNodeWithTag("favorite-card/${first.id}").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("favorite-details/${first.id}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("favorite-details/${second.id}").assertDoesNotExist()
        compose.onNodeWithTag("favorite-details/${first.id}").assertExists()
        assertEquals(Destination.ContactFavoriteDetail(first.id), model.state.value.destination)
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
        compose.runOnIdle { assertEquals(Destination.ContactFavoriteDetail(entries.first().id), model.state.value.destination) }
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

    @Test fun rectangularQrUsesTheRemainingSpaceAndOpensPreview() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val wide = ProfileTile(id = "wide", kind = ProfileTileKind.QR, title = "横向标题", text = "https://example.invalid/wide", columns = 3)
        val tall = wide.copy(id = "tall", title = "竖向标题", columns = 1, rows = 2)
        val wideCode = app.uiCopy("profile.contact_qr", "name" to wide.title, locale = "zh-CN")
        val tallCode = app.uiCopy("profile.contact_qr", "name" to tall.title, locale = "zh-CN")
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.DARK)) {
            CompositionLocalProvider(LocalUiLocale provides "zh-CN") {
                ProfileTileBoard(listOf(wide, tall), emptyList(), { null }, Modifier.width(360.dp))
            }
        } }
        compose.waitUntil(5000) {
            compose.onAllNodes(hasContentDescription(wideCode), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodes(hasContentDescription(tallCode), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        val horizontalCode = compose.onNodeWithContentDescription(wideCode, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val verticalCode = compose.onNodeWithContentDescription(tallCode, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(compose.onNodeWithText("横向标题", useUnmergedTree = true).getUnclippedBoundsInRoot().left >= horizontalCode.right)
        assertTrue(compose.onNodeWithText("竖向标题", useUnmergedTree = true).getUnclippedBoundsInRoot().top >= verticalCode.bottom)
        compose.onNodeWithTag("profile-tile/wide").performClick()
        compose.onNodeWithText(app.uiCopy("profile.copy_contents", locale = "zh-CN")).assertIsDisplayed()
    }

    @Test fun shareFormatsPutLongImageAboveTheTwoFilesAndPersistTheSourceSwitch() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val model = testAppModel(app)
        compose.activity.viewModelStore.put("share-format-test", model)
        val preference = app.getSharedPreferences("sharing", 0)
        preference.edit().remove("long_image_embed_source").commit()
        var shared: com.kigtts.next.sharing.ProfileShareFormat? = null
        compose.setContent { KigttsTheme(ThemeSettings()) {
            val checked by model.sharing.embedLongImage.collectAsState()
            com.kigtts.next.sharing.ProfileShareFormatTiles(checked, model.sharing::setEmbedLongImage) { shared = it }
        } }
        val source = compose.onNodeWithTag("profile-embed-source")
        val photo = compose.onNodeWithTag("profile-format-long").getUnclippedBoundsInRoot()
        val paper = compose.onNodeWithTag("profile-share-pdf").getUnclippedBoundsInRoot()
        val web = compose.onNodeWithTag("profile-share-mhtml").getUnclippedBoundsInRoot()
        assertTrue(photo.bottom <= paper.top && paper.top == web.top && paper.right <= web.left)
        source.assertIsOff().performClick().assertIsOn()
        assertTrue(preference.getBoolean("long_image_embed_source", false))
        assertNull(shared)
        compose.onNodeWithTag("profile-share-long").performClick()
        assertEquals(com.kigtts.next.sharing.ProfileShareFormat.LONG_IMAGE, shared)
        source.performClick().assertIsOff()
    }
}
