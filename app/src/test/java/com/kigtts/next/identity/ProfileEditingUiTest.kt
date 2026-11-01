package com.kigtts.next.identity

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.testAppModel
import com.kigtts.next.theme.*
import com.kigtts.next.ui.identity.*
import com.kigtts.next.ui.navigation.SecondaryDestination
import com.kigtts.next.state.Destination
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED) @Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class ProfileEditingUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun tagEditControlsAppearWhileTheirSpacingIsStillChanging() {
        var editing by mutableStateOf(false)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Box(Modifier.width(360.dp).padding(16.dp)) { ProfileEditableTags(listOf("摄影", "开发"), editing, {}, {}) }
        } }
        val start = compose.onNodeWithTag("profile-tag-body/摄影", true).getUnclippedBoundsInRoot().top
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { editing = true }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame(); compose.mainClock.advanceTimeBy(100); compose.waitForIdle()
        compose.onNodeWithTag("profile-tag-remove/摄影").assertIsDisplayed()
        compose.onNodeWithTag("profile-add-tag").assertIsDisplayed()
        val middle = compose.onNodeWithTag("profile-tag-body/摄影", true).getUnclippedBoundsInRoot().top
        compose.mainClock.advanceTimeBy(300); compose.waitForIdle()
        val end = compose.onNodeWithTag("profile-tag-body/摄影", true).getUnclippedBoundsInRoot().top
        assertTrue("控件出现时仍在同时腾出间距", middle > start && middle < end)
        compose.mainClock.autoAdvance = true
    }

    @Test fun editingTagBodiesAlignWithTheAddButtonWhileDeleteBadgesStayAbove() {
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Box(Modifier.width(360.dp).padding(16.dp)) { ProfileEditableTags(listOf("摄影", "开发"), true, {}, {}) }
        } }
        val add = compose.onNodeWithTag("profile-add-tag").getUnclippedBoundsInRoot()
        listOf("摄影", "开发").forEach { tag ->
            val body = compose.onNodeWithTag("profile-tag-body/$tag", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val remove = compose.onNodeWithTag("profile-tag-remove/$tag").getUnclippedBoundsInRoot()
            assertEquals("标签与添加按钮应垂直居中对齐", (add.top + add.bottom).value / 2,
                (body.top + body.bottom).value / 2, .5f)
            assertTrue("删除徽标叠加在标签右上角", remove.top < body.top)
        }
        compose.onRoot().captureRoboImage("build/previews/profile-tags-aligned.png")
    }

    @Test fun longPressReordersTagsWithoutClickingDeleteOrMovingTheWholePage() {
        var tags by mutableStateOf(listOf("摄影", "开发", "音乐"))
        var editing by mutableStateOf(true)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Box(Modifier.width(360.dp).padding(16.dp)) { ProfileEditableTags(tags, editing, { tags = it }, {}) }
        } }
        val source = compose.onNodeWithTag("profile-tag/摄影").getUnclippedBoundsInRoot()
        val target = compose.onNodeWithTag("profile-tag/音乐").getUnclippedBoundsInRoot()
        val density = compose.density.density
        compose.onNodeWithTag("profile-tag/摄影").performTouchInput {
            down(Offset(12 * density, height / 2f)); advanceEventTime(650)
            moveBy(Offset(((target.left + target.right - source.left - source.right) / 2).value * density, 0f), 300); up()
        }
        compose.waitForIdle()
        assertEquals(listOf("开发", "音乐", "摄影"), tags)
        compose.onNodeWithTag("profile-tag-remove/音乐").performClick()
        assertEquals(listOf("开发", "摄影"), tags)
        compose.runOnIdle { editing = false }
        compose.onNodeWithTag("profile-tag-remove/摄影").assertDoesNotExist()
    }

    @Test fun favoritesSearchIsInTheToolbarAndResultOpensTheOffscreenCard() {
        val model = testAppModel(ApplicationProvider.getApplicationContext<Application>())
        compose.activity.viewModelStore.put("favorites-search", model)
        val target = model.contactFavorites.save(JSONObject().put("identityId", "target").put("nickname", "摄影朋友")
            .put("tags", org.json.JSONArray(listOf("音乐", "广州"))))
        repeat(60) { model.contactFavorites.save(JSONObject().put("identityId", "friend-$it").put("nickname", "朋友 $it")) }
        model.navigate(Destination.ContactFavorites)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            val state by model.state.collectAsState()
            SecondaryDestination(state.destination, state, model)
        } }
        compose.onNodeWithTag("favorite-search-input").assertDoesNotExist()
        compose.onNodeWithTag("search-contact-favorites").performClick()
        compose.onNodeWithTag("favorite-search-input").performTextInput("摄影 广州")
        compose.waitUntil(5000) { compose.onAllNodesWithText("摄影朋友").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("摄影朋友").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("favorite-details/${target.id}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("favorite-search-input").assertDoesNotExist()
        compose.onNodeWithTag("favorite-details/${target.id}").assertExists()
        compose.onRoot().captureRoboImage("build/previews/favorites-search-result.png")
    }

    @Test fun restoringAnEditDraftAndPressingBackSavesBeforeLeavingEditMode() {
        val model = testAppModel(ApplicationProvider.getApplicationContext<Application>())
        compose.activity.viewModelStore.put("profile-restoration", model)
        model.identities.save(model.identities.create().copy(nickname = "我的名片", tags = listOf("摄影", "音乐")))
        model.navigate(Destination.PersonalCards)
        val restoration = StateRestorationTester(compose)
        restoration.setContent { KigttsTheme(ThemeSettings()) {
            val state by model.state.collectAsState()
            SecondaryDestination(state.destination, state, model)
        } }
        compose.onNodeWithTag("edit-personal-information").performClick()
        compose.onNodeWithTag("profile-tag-remove/摄影").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("profile-tag/摄影").assertDoesNotExist()
        compose.onNodeWithTag("profile-tag-remove/音乐").assertExists()
        compose.onNodeWithTag("page-back").performClick()
        compose.waitUntil(5000) { model.identities.current?.tags == listOf("音乐") }
        compose.runOnIdle { assertEquals(Destination.PersonalCards, model.state.value.destination) }
        compose.onNodeWithTag("profile-tag-remove/音乐").assertDoesNotExist()
        compose.onNodeWithTag("page-back").performClick()
        compose.runOnIdle { assertEquals(Destination.Home, model.state.value.destination) }
    }
}
