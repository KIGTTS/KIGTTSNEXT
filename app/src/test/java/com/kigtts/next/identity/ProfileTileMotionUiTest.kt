package com.kigtts.next.identity

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
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
class ProfileTileMotionUiTest {
    @Test fun anIndependentOneCellSocialTileOpensItsOwnAccount() {
        val contact = ContactLink("GitLab", "https://gitlab.com/me", "account-qr")
        val socialTile = ProfileTile(id = "independent", kind = ProfileTileKind.CONTACT, columns = 1, social = contact)
        var opened: ContactLink? = null
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Box(Modifier.width(360.dp)) { ProfileTileBoard(listOf(socialTile), emptyList(), { null }, openContact = { opened = it }) }
        } }
        compose.onNodeWithContentDescription("GitLab").performClick()
        compose.runOnIdle { assertEquals(contact, opened) }
        compose.onNodeWithText(contact.value).assertDoesNotExist()
    }

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun tile(id: String) = compose.onNodeWithTag("profile-tile/$id")
    private fun width(id: String) = tile(id).getUnclippedBoundsInRoot().let { (it.right - it.left).value }
    private fun left(id: String) = tile(id).getUnclippedBoundsInRoot().left.value

    @Test fun holdingTheTileFacePicksItUpBeforeMovementAndSmallMovesFollowOneForOne() {
        val events = mutableListOf<HapticFeedbackType>()
        var edits = 0
        val tiles = listOf(ProfileTile(id = "a", title = "A"))
        compose.setContent { KigttsTheme(ThemeSettings()) {
            CompositionLocalProvider(LocalHapticFeedback provides object : HapticFeedback {
                override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { events += hapticFeedbackType }
            }) {
                ProfileTileBoard(tiles, emptyList(), { null }, Modifier.width(360.dp), editing = true, edit = { edits++ })
            }
        } }
        val initial = left("a")
        val board = compose.onNodeWithTag("profile-tiles")
        val tileBounds = tile("a").getUnclippedBoundsInRoot()
        val boardBounds = board.getUnclippedBoundsInRoot()
        val anchor = with(compose.density) {
            Offset(((tileBounds.left + tileBounds.right) / 2 - boardBounds.left).toPx(),
                ((tileBounds.top + tileBounds.bottom) / 2 - boardBounds.top).toPx())
        }
        board.performTouchInput { down(anchor) }
        compose.mainClock.advanceTimeBy(700)
        compose.runOnIdle { assertTrue(HapticFeedbackType.GestureThresholdActivate in events) }
        board.performTouchInput { moveTo(anchor + Offset(6f, 0f)) }
        assertEquals(initial + 2f, left("a"), .5f)
        board.performTouchInput { moveTo(anchor + Offset(12f, 0f)) }
        assertEquals(initial + 4f, left("a"), .5f)
        tile("a").performTouchInput { up() }
        compose.runOnIdle { assertEquals(0, edits) }
        tile("a").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(1, edits) }
    }

    @Test fun fractionalResizeFollowsBeforeChangingSpanAndReturnsFromTheReleasedSize() {
        var tiles by mutableStateOf(listOf(ProfileTile(id = "a", title = "文字", columns = 2)))
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
            Box(Modifier.width(360.dp)) { ProfileTileBoard(tiles, emptyList(), { null }, editing = true, change = { tiles = it }) }
        } }
        val initial = width("a")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("tile-resize/a").performTouchInput { down(center); moveBy(Offset(60f, 30f), 120) }
        val held = width("a")
        assertTrue("跨格前也应连续改变像素尺寸", held > initial + 3)
        assertEquals(2, tiles.single().columns)
        compose.onNodeWithTag("tile-resize/a").performTouchInput { up() }
        assertEquals("松手首帧不能跳回网格", held, width("a"), 1f)
        compose.mainClock.advanceTimeBy(96)
        val returning = width("a")
        assertTrue("应从释放尺寸平滑归位", returning in initial..held && returning < held)
        compose.mainClock.advanceTimeBy(300)
        assertEquals(initial, width("a"), 1f)
    }

    @Test fun reorderDropKeepsItsFloatingPositionUntilItReachesTheNewSlot() {
        var tiles by mutableStateOf(listOf(ProfileTile(id = "a", title = "A"), ProfileTile(id = "b", title = "B")))
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
            Box(Modifier.width(360.dp)) { ProfileTileBoard(tiles, emptyList(), { null }, editing = true, change = { tiles = it }) }
        } }
        compose.mainClock.autoAdvance = false
        tile("a").performTouchInput { down(center); advanceEventTime(700); moveBy(Offset(400f, 0f), 160) }
        assertEquals(listOf("b", "a"), tiles.map { it.id })
        val held = left("a")
        tile("a").performTouchInput { up() }
        assertEquals("释放后保留当前浮层位置", held, left("a"), 1f)
        compose.mainClock.advanceTimeBy(96)
        val middle = left("a")
        assertTrue(middle > held + 1)
        compose.onRoot().captureRoboImage("build/previews/profile-tile-mid-drop.png")
        compose.mainClock.advanceTimeBy(300)
        assertEquals(186f, left("a"), 1f)
    }

    @Test fun oneCellIconTilesHideTextButKeepTheirActionsAndLargerTilesKeepDetails() {
        val contact = ContactLink("QQ", "12345")
        val tiles = listOf(ProfileTile(id = "social", kind = ProfileTileKind.CONTACT, title = "社交小磁贴", contact = contact.value, columns = 1),
            ProfileTile(id = "link", kind = ProfileTileKind.LINK, title = "链接小磁贴", text = "https://example.com", columns = 1),
            ProfileTile(id = "wide", kind = ProfileTileKind.LINK, title = "较大磁贴", text = "https://example.org", columns = 2))
        var openedContact = false; var url = ""
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
            Box(Modifier.width(360.dp)) { ProfileTileBoard(tiles, listOf(contact), { null },
                openContact = { openedContact = true }, openLink = { url = it }) }
        } }
        compose.onNodeWithText("社交小磁贴").assertDoesNotExist()
        compose.onNodeWithText("链接小磁贴").assertDoesNotExist()
        compose.onNodeWithText(contact.value).assertDoesNotExist()
        compose.onNodeWithText("https://example.com").assertDoesNotExist()
        compose.onNodeWithText("较大磁贴").assertIsDisplayed()
        compose.onNodeWithText("https://example.org").assertIsDisplayed()
        compose.onNodeWithContentDescription("社交小磁贴").performClick(); assertTrue(openedContact)
        compose.onNodeWithContentDescription("链接小磁贴").performClick(); assertEquals("https://example.com", url)
        compose.onRoot().captureRoboImage("build/previews/profile-one-cell-icons.png")
    }
}
