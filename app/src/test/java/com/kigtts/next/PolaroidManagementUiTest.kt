package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.BackEventCompat
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.*
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.cards.*
import com.kigtts.next.media.MediaRepository
import com.kigtts.next.theme.*
import com.kigtts.next.ui.cards.*
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PluginPaths
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED) @Config(sdk=[35],qualifiers="zh-rCN-w393dp-h852dp-xxhdpi")
class PolaroidManagementUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private class Fixture {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val external=requireNotNull(app.getExternalFilesDir(null)).canonicalFile
        val root=File(external,"appdata/Kigtts/cards").canonicalFile
        init {check(root.toPath().startsWith(external.toPath())&&root!=external);root.deleteRecursively()
            app.getSharedPreferences("contact-cards",0).edit().clear().commit();app.getSharedPreferences("cards-view",0).edit().clear().commit()}
        val paths=PluginPaths(app);val stickers=StickerRepository(app,paths,MediaRepository(app,paths));val cards=CardRepository(app,stickers)
        val a=cards.create(PolaroidCard(title="A"));val b=cards.create(PolaroidCard(title="B"));val c=cards.create(PolaroidCard(title="C"))
        val target=cards.createAlbum("目标相册","target.album")
        init {cards.orderCards(CardRepository.DEFAULT_ALBUM,listOf(a.id,b.id,c.id));stickers.markMigrated()}
        val haptics=mutableListOf<HapticFeedbackType>();var backs=0;val edits=mutableListOf<String>()
    }
    private fun show(f:Fixture) {
        val haptics=object:HapticFeedback {override fun performHapticFeedback(hapticFeedbackType:HapticFeedbackType){f.haptics+=hapticFeedbackType}}
        compose.setContent {CompositionLocalProvider(LocalHapticFeedback provides haptics) {KigttsTheme(ThemeSettings()) {
            CardPageScaffold("拍立得",{f.backs++}) {CardsScreen(f.cards,f.stickers,{}, {f.edits+=it},{})}
        }}}
        // The management fixtures exercise expanded rails; the native page now starts folded.
        compose.expandAlbumGroups()
        compose.waitForIdle()
    }
    private fun action(label:String) {
        compose.onNodeWithContentDescription("更多页面操作").performClick();compose.onNodeWithText(label).performClick()
    }
    private fun selectGrid(f:Fixture) {
        compose.onNodeWithTag("cards-browse-mode").performClick()
        compose.onNodeWithTag("polaroid/"+f.a.id).performTouchInput {longClick()}
        compose.onNodeWithTag("card-management-grid").assertIsDisplayed()
    }
    @Test fun gridLongPressSelectsAndConfirmedBatchMoveAndDeleteStayAtomicWithNormalPreviewAndEditPreserved() {
        val f=Fixture();show(f);selectGrid(f)
        compose.onNodeWithTag("manage-photo/"+f.a.id).assertIsSelected()
        compose.onNodeWithTag("manage-photo/"+f.b.id).performClick().assertIsSelected()
        assertTrue(f.edits.isEmpty());assertEquals(3,f.cards.cardsInAlbum(CardRepository.DEFAULT_ALBUM).size)
        action("移动所选照片");compose.onNodeWithTag("move-card-album/"+f.target.id).performClick()
        compose.onNodeWithTag("confirm-move-cards").assertIsDisplayed()
        assertTrue(f.cards.cardsInAlbum(f.target.id).isEmpty())
        compose.onNodeWithText("取消").performClick();assertTrue(f.cards.cardsInAlbum(f.target.id).isEmpty())
        action("移动所选照片");compose.onNodeWithTag("move-card-album/"+f.target.id).performClick()
        compose.onNodeWithTag("confirm-move-cards").performClick()
        compose.waitUntil(8_000){f.cards.cardsInAlbum(f.target.id).map {it.id}==listOf(f.a.id,f.b.id)}
        compose.onNodeWithTag("card-management").assertDoesNotExist()
        compose.onNodeWithTag("polaroid/"+f.a.id).performClick();compose.onNodeWithTag("card-preview-content").assertIsDisplayed()
        compose.onNodeWithTag("edit-card-preview").performClick();compose.waitForIdle();assertEquals(listOf(f.a.id),f.edits)
        compose.onNodeWithTag("polaroid/"+f.a.id).performTouchInput {longClick()}
        compose.onNodeWithTag("manage-photo/"+f.b.id).performClick()
        action("删除所选照片");compose.onNodeWithText("删除 2 张照片？").assertIsDisplayed()
        assertEquals(2,f.cards.cardsInAlbum(f.target.id).size);compose.onNodeWithText("取消").performClick()
        assertEquals(2,f.cards.cardsInAlbum(f.target.id).size)
        action("删除所选照片");compose.onNodeWithText("删除").performClick()
        compose.waitUntil(8_000){f.cards.cardsInAlbum(f.target.id).isEmpty()}
        assertEquals(listOf(f.c.id),f.cards.cards.value.map {it.id});assertEquals(0,f.backs)
    }
    @Test fun gridHandleSortingCommitsOnceWithPickupSnapReleaseHapticsAndThePaperSettlesBack() {
        val f=Fixture();show(f);selectGrid(f)
        val revision=f.stickers.snapshot.value.revision
        val pane=compose.onNodeWithTag("card-management-sort").fetchSemanticsNode().boundsInRoot
        val grip=compose.onNodeWithTag("manage-photo-grip/"+f.a.id,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val target=compose.onNodeWithTag("manage-photo/"+f.c.id).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("card-management-sort").performTouchInput {
            down(grip.center-pane.topLeft);moveTo(target.center-pane.topLeft,delayMillis=300);advanceEventTime(300);up()
        }
        compose.waitUntil(8_000){f.cards.cardsInAlbum(CardRepository.DEFAULT_ALBUM).map {it.id}==listOf(f.b.id,f.c.id,f.a.id)}
        compose.waitForIdle();assertEquals(revision+1,f.stickers.snapshot.value.revision)
        assertTrue(f.haptics.contains(HapticFeedbackType.GestureThresholdActivate));assertTrue(f.haptics.contains(HapticFeedbackType.SegmentFrequentTick))
        assertTrue(f.haptics.contains(HapticFeedbackType.GestureEnd))
        compose.onNodeWithTag("manage-photo/"+f.a.id).assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag("page-back").performClick();compose.onNodeWithTag("card-management").assertDoesNotExist();assertEquals(0,f.backs)
    }
    @Test fun stackLongPressExplodesIntoAHorizontalSelectionAndPredictiveBackExitsOnlyManagement() {
        val f=Fixture();show(f)
        compose.onNodeWithTag("polaroid/"+f.a.id).performTouchInput {longClick()}
        compose.onNodeWithTag("card-management-strip").assertIsDisplayed();compose.onNodeWithTag("manage-photo/"+f.a.id).assertIsSelected()
        val dispatcher=compose.activity.onBackPressedDispatcher
        compose.runOnIdle {dispatcher.dispatchOnBackStarted(BackEventCompat(0f,100f,0f,BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(100f,100f,.5f,BackEventCompat.EDGE_LEFT));dispatcher.onBackPressed()}
        compose.onNodeWithTag("card-management").assertDoesNotExist();compose.onNodeWithTag("cards-stack").assertIsDisplayed()
        assertEquals(0,f.backs);assertTrue(f.edits.isEmpty());assertEquals(3,f.cards.cards.value.size)
    }
    @Test fun albumLongPressSupportsMultiSelectionAndDeletingAlbumsKeepsEveryPhotoInDefaultAlbum() {
        val f=Fixture();show(f)
        compose.onNodeWithTag("card-album/"+f.target.id).performTouchInput {longClick()}
        compose.onNodeWithTag("card-management-albums").assertIsDisplayed()
        compose.onNodeWithTag("manage-album/"+f.target.id).assertIsSelected()
        compose.onNodeWithTag("cards-manage-select-all").performClick()
        action("删除所选相册");compose.onNodeWithText("删除 1 个相册？").assertIsDisplayed()
        assertEquals(2,f.cards.albums.value.size);compose.onNodeWithText("删除").performClick()
        compose.waitUntil(8_000){f.cards.albums.value.size==1}
        assertEquals(CardRepository.DEFAULT_ALBUM,f.cards.albums.value.single().id);assertEquals(3,f.cards.cards.value.size)
        assertEquals(listOf(f.a.id,f.b.id,f.c.id),f.cards.cardsInAlbum(CardRepository.DEFAULT_ALBUM).map {it.id})
        compose.onNodeWithTag("cards-manage-done").performClick();assertEquals(0,f.backs)
    }
}
