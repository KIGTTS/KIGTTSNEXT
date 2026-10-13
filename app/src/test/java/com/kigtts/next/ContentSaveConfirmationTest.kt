package com.kigtts.next

import android.app.Application
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.media.MediaRepository
import com.kigtts.next.state.*
import com.kigtts.next.text.TextFormat
import com.kigtts.next.text.TextFormatRun
import com.kigtts.plugin.runtime.PluginPaths
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ContentSaveConfirmationTest {
    private class Fixture {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repository = MediaRepository(app, PluginPaths(app))
        val library: QuickTextLibrary
        var state = AppState(editorValue = TextFieldValue("旧正文"))
        var returned = 0
        var text = "旧正文"
        val content: ContentWorkspace
        init {
            app.getSharedPreferences("quick-phrases", 0).edit().clear().commit()
            library = QuickTextLibrary(QuickPhraseStore(app))
            content = ContentWorkspace(app, repository, library, { state }, { state = it(state) },
                { state = state.copy(destination = it) }, { returned++; state = state.copy(destination = Destination.Home) }, { text = it })
        }
    }

    @Test fun successfulSaveRetainsThePendingEditorUntilAnimatedCleanupAndTheNextEditorStartsClean() = runBlocking {
        val f = Fixture()
        f.content.editor(target = "subtitle")
        val formats = listOf(TextFormatRun(0, 2, TextFormat.BOLD))
        f.content.editEditor("新正文", "文本", formats, 2)
        val retained = f.content.editorDraft.value
        assertTrue(f.content.requestDiscard())
        var cleanup: (() -> Unit)? = null
        f.content.saveEditor("新正文", "文本") { cleanup = it }
        assertEquals("新正文", f.text)
        assertEquals(formats, f.state.subtitleFormats)
        assertEquals(retained, f.content.editorDraft.value)
        assertTrue(f.content.discardRequested.value)
        assertEquals(0, f.returned)
        requireNotNull(cleanup).invoke()
        assertNull(f.content.editorDraft.value)
        assertFalse(f.content.discardRequested.value)
        assertEquals(1, f.returned)
        requireNotNull(cleanup).invoke()
        assertEquals("Accepted cleanup must not pop another page", 1, f.returned)
        f.content.editor()
        assertNotNull(f.content.editorDraft.value)
        assertFalse(f.content.discardRequested.value)
    }

    @Test fun failedHtmlWriteRetainsTheSameDraftAndConfirmationAndCanBeRetried() = runBlocking {
        val f = Fixture()
        val media = f.repository.html("页面", "旧HTML")
        f.content.editorFile(media.id, "index.html", "display")
        f.content.editEditor("新HTML", "index.html", selection = 3)
        val retained = f.content.editorDraft.value
        assertTrue(f.content.requestDiscard())
        val file = f.repository.htmlFiles.file(media.id, "index.html")
        assertTrue(file.delete())
        var callbacks = 0
        val failure = runCatching { f.content.saveEditor("新HTML", "index.html") { callbacks++ } }.exceptionOrNull()
        assertNotNull(failure)
        assertEquals(retained, f.content.editorDraft.value)
        assertTrue(f.content.discardRequested.value)
        assertEquals(0, f.returned)
        assertEquals(0, callbacks)
        file.writeText("旧HTML", Charsets.UTF_8)
        f.content.saveEditor("新HTML", "index.html") { cleanup -> callbacks++; cleanup() }
        assertEquals("新HTML", f.repository.htmlFiles.read(media.id, "index.html"))
        assertNull(f.content.editorDraft.value)
        assertFalse(f.content.discardRequested.value)
        assertEquals(1, f.returned)
        assertEquals(1, callbacks)
    }

    @Test fun invalidPhraseSaveKeepsItsRealDraftUntilAValidRetryCommitsIt() {
        val f = Fixture()
        val category = f.library.categories.value.first()
        val original = f.library.phrases.value.first()
        f.content.beginPhrase(original, category)
        f.content.editPhrase { it.copy(text = "保留待保存正文", category = "不存在的分组") }
        val retained = f.content.phraseDraft.value
        assertFalse(f.content.savePhrase())
        assertEquals(retained, f.content.phraseDraft.value)
        assertEquals(original, f.library.phrases.value.first { it.id == original.id })
        f.content.editPhrase { it.copy(category = category) }
        assertTrue(f.content.savePhrase())
        assertNull(f.content.phraseDraft.value)
        val restored = QuickTextLibrary(QuickPhraseStore(f.app))
        assertEquals("保留待保存正文", restored.phrases.value.first { it.id == original.id }.text)
    }
}
