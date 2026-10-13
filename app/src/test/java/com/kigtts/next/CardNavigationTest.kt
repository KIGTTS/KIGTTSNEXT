package com.kigtts.next

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.state.*
import com.kigtts.plugin.api.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CardNavigationTest {
    @Test fun openingCardsFromAgentReturnsToTheCallingPage() {
        val model = testAppModel(ApplicationProvider.getApplicationContext<Application>())
        val source = Destination.PluginPage("tools.cloud-agent", "chat")
        model.navigate(source); model.cardUi.open(StickerUiTarget.CARDS, null)
        assertEquals(Destination.Cards, model.state.value.destination)
        model.navigateBack(); assertEquals(source, model.state.value.destination)
    }
    @Test fun retainedEditorStillBlocksPluginWritesWhileItsLibraryChildIsOpen() = runBlocking {
        val model = testAppModel(ApplicationProvider.getApplicationContext<Application>())
        val card = model.cards.create(PolaroidCard(title = "正在编辑"))
        model.navigate(Destination.CardEditor(card.id)); model.navigate(Destination.StickerLibrary())
        val broker = model.stickerPlugins.forPlugin("test.card-tools", setOf("stickers-write"))
        val reply = broker.request(StickerCommand.Prepare("retained-draft", model.stickers.snapshot.value.revision,
            listOf(StickerEdit.PutCard(card.copy(title = "插件修改")))))
        assertEquals(StickerError.BUSY, (reply as StickerReply.Failure).code)
        assertEquals("正在编辑", model.cards.cards.value.first { it.id == card.id }.title)
        model.navigateBack(); assertEquals(Destination.CardEditor(card.id), model.state.value.destination)
    }
}
