@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.kigtts.next

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.cards.*
import com.kigtts.next.media.*
import com.kigtts.next.state.*
import com.kigtts.next.ui.cards.*
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CardQuickExportTest {
    private fun model(): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val external = requireNotNull(app.getExternalFilesDir(null)).canonicalFile
        listOf("cards", "media").forEach { name ->
            val directory = File(external, "appdata/Kigtts/$name").canonicalFile
            check(directory.toPath().startsWith(external.toPath()) && directory != external)
            directory.deleteRecursively()
        }
        return testAppModel(app)
    }
    private fun photo(model: AppViewModel): PolaroidCard {
        val app = model.getApplication<Application>()
        val file = File(app.cacheDir, "quick-polaroid.png")
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val image = model.stickers.importImage(Uri.fromFile(file)); file.delete()
        return model.cards.create(PolaroidCard(title = "照片标题", description = "发送的字幕", imageId = image.id,
            frameStyleId = "instax.mini.classic"))
    }

    @Test fun noFrameExportCreatesMediaDraftAndExitKeepsTheSubmissionLockedUntilConsumedOnce() = runTest {
        val model = model(); val card = photo(model)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        var callbacks = 0
        val actions = CardWorkspaceActions(model.getApplication(), model.cards, model.stickers, scope, {}, {},
            { id, text -> callbacks++; model.quickImageCard(id, text) }, dispatcher)
        val accepted = CompletableDeferred<() -> Unit>()
        try {
            actions.cardAction(card, CardMenuAction.QUICK)
            assertEquals(card, actions.quickCard); assertTrue(model.mediaRepository.entries.value.isEmpty())
            actions.publishQuick(false, false) { accepted.complete(it) }
            val cleanup = accepted.await()
            assertTrue(actions.busy); assertEquals(0, callbacks)
            val media = model.mediaRepository.entries.value.single()
            assertEquals(MediaKind.IMAGE, media.kind)
            val bitmap = requireNotNull(requireNotNull(model.mediaRepository.file(media)).inputStream().use { BitmapFactory.decodeStream(it) })
            try { assertEquals(PolaroidFormat.MINI.geometry.photoAspectRatio, bitmap.width.toDouble() / bitmap.height, .01) }
            finally { bitmap.recycle() }
            actions.publishQuick(true, true) { error("A duplicate submit must not be accepted") }
            advanceUntilIdle(); assertEquals(listOf(media), model.mediaRepository.entries.value)
            cleanup(); cleanup()
            assertFalse(actions.busy); assertNull(actions.quickCard); assertEquals(1, callbacks)
            assertEquals(card, model.cards.cards.value.single())
            val draft = requireNotNull(model.content.phraseDraft.value)
            assertEquals(QuickCardType.MEDIA, draft.type); assertEquals(media.id, draft.mediaId)
            assertEquals("发送的字幕", draft.text)
            assertTrue(draft.valid(model.mediaRepository))
            model.content.editPhrase { it.copy(text = "", title = "") }
            assertTrue(requireNotNull(model.content.phraseDraft.value).valid(model.mediaRepository))
            assertTrue(model.content.savePhrase())
            val saved = model.phrases.value.first { it.mediaId == media.id }
            assertEquals(QuickCardType.MEDIA, saved.type); assertEquals("", saved.text)
        } finally { scope.cancel() }
    }

    @Test fun missingSourceKeepsTheExportChoiceAndDoesNotCreateAMediaOrQuickItem() = runTest {
        val model = model(); val card = photo(model)
        val dispatcher = StandardTestDispatcher(testScheduler); val messages = mutableListOf<String>()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val actions = CardWorkspaceActions(model.getApplication(), model.cards, model.stickers, scope, {}, { messages += it },
            { _, _ -> error("A failed export must not navigate") }, dispatcher)
        try {
            actions.cardAction(card, CardMenuAction.QUICK); model.cards.remove(card.id)
            actions.publishQuick(true, true) { error("A failed export must not close the dialog") }
            advanceUntilIdle()
            assertFalse(actions.busy); assertEquals(card, actions.quickCard)
            assertTrue(model.mediaRepository.entries.value.isEmpty()); assertNull(model.content.phraseDraft.value)
            assertTrue(messages.single().contains("已移除"))
            actions.cancelQuick(); assertNull(actions.quickCard)
        } finally { scope.cancel() }
    }
}
