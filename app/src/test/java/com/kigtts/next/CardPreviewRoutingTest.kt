@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.kigtts.next

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.state.*
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CardPreviewRoutingTest {
    @Before fun mainDispatcher() { Dispatchers.setMain(Dispatchers.Unconfined) }
    @After fun resetDispatcher() { Dispatchers.resetMain() }
    private fun model() = testAppModel(ApplicationProvider.getApplicationContext<Application>())

    @Test fun pluginPreviewChoosesTheOwningAlbumAndKeepsTheCallingPageInHistory() = runBlocking {
        val model = model(); val album = model.cards.createAlbum("旅行")
        val card = model.cards.create(PolaroidCard(title = "旅行照片"), album.id)
        val source = Destination.PluginPage("tools.cloud-agent", "chat")
        model.navigate(source)
        val reply = model.stickerPlugins.forPlugin("test.card-tools", setOf("stickers-write"))
            .request(StickerCommand.OpenUi(StickerUiTarget.CARDS, card.id))
        assertTrue(reply is StickerReply.UiOpened); assertEquals(Destination.Cards, model.state.value.destination)
        val request = model.cardUi.previewRequest.value
        assertEquals(card.id, request.cardId); assertEquals(album.id, model.cardUi.selectedAlbum.value)
        model.cardUi.previewHandled(request.version)
        assertNull(model.cardUi.previewRequest.value.cardId)
        assertEquals(Destination.Cards, model.state.value.destination)
        model.navigateBack(); assertEquals(source, model.state.value.destination)
        assertEquals(album.id, model.cardUi.selectedAlbum.value)
    }

    @Test fun nonexistentPluginPreviewCannotNavigateOrPublishAFakeRequest() = runBlocking {
        val model = model(); val source = Destination.PluginPage("tools.cloud-agent", "chat")
        model.navigate(source)
        val reply = model.stickerPlugins.forPlugin("test.card-tools", setOf("stickers-write"))
            .request(StickerCommand.OpenUi(StickerUiTarget.CARDS, "missing-card"))
        assertEquals(StickerError.NOT_FOUND, (reply as StickerReply.Failure).code)
        assertEquals(source, model.state.value.destination)
        assertEquals(0L, model.cardUi.previewRequest.value.version)
        assertFalse(model.cardUi.open(StickerUiTarget.CARDS, "missing-card"))
        assertEquals(source, model.state.value.destination)
    }

    @Test fun reopeningTheSameCardCreatesANewRequestAndAnOldAcknowledgementCannotConsumeIt() {
        val model = model(); val card = model.cards.create(PolaroidCard(title = "重复预览"))
        val source = Destination.PluginPage("tools.cloud-agent", "chat"); model.navigate(source)
        assertTrue(model.cardUi.open(StickerUiTarget.CARDS, card.id))
        val first = model.cardUi.previewRequest.value
        assertTrue(model.cardUi.open(StickerUiTarget.CARDS, card.id))
        val second = model.cardUi.previewRequest.value
        assertEquals(first.version + 1, second.version)
        model.cardUi.previewHandled(first.version); assertEquals(second, model.cardUi.previewRequest.value)
        model.cardUi.previewHandled(second.version); assertNull(model.cardUi.previewRequest.value.cardId)
        model.navigateBack(); assertEquals(source, model.state.value.destination)
    }

    @Test fun olderPreviewDestinationUsesTheSameRequestInsteadOfCreatingAPreviewPage() {
        val model = model(); val card = model.cards.create(PolaroidCard(title = "兼容预览"))
        val source = Destination.PluginPage("tools.cloud-agent", "chat"); model.navigate(source)
        model.navigate(Destination.CardPreview(card.id))
        assertEquals(Destination.Cards, model.state.value.destination)
        assertEquals(card.id, model.cardUi.previewRequest.value.cardId)
        assertFalse(model.state.value.navigationStack.any { it is Destination.CardPreview })
        model.navigateBack(); assertEquals(source, model.state.value.destination)
    }

    @Test fun openingOnlyTheAlbumListClearsAnUnconsumedPreviewWithoutResettingItsAlbum() {
        val model = model(); val album = model.cards.createAlbum("收藏")
        val card = model.cards.create(PolaroidCard(title = "收藏照片"), album.id)
        model.cardUi.open(StickerUiTarget.CARDS, card.id)
        val version = model.cardUi.previewRequest.value.version
        model.cardUi.open(StickerUiTarget.CARDS, null)
        assertNull(model.cardUi.previewRequest.value.cardId); assertEquals(version, model.cardUi.previewRequest.value.version)
        assertEquals(album.id, model.cardUi.selectedAlbum.value)
    }
}
