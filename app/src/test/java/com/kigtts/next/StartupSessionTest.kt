package com.kigtts.next

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.state.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35])
class StartupSessionTest {
    private lateinit var app: Application
    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        listOf("media-presentation", "appearance", "playback").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
    }

    @Test fun newSessionIgnoresTheLastPageCardButKeepsTheLibraryAndPreferences() {
        val previous = testAppModel(app)
        previous.setSubtitlePlaceholder("准备好表达")
        previous.setSubtitleSizePercent(16f)
        val page = previous.mediaRepository.html("旧页面", "<h1>不应自动打开</h1>")
        try {
            val phrase = QuickPhrase("网页", "常用", mediaId = page.id, type = QuickCardType.PAGE)
            previous.quickTexts.savePhrase(phrase)
            previous.content.send(phrase)
            previous.navigate(Destination.Plugins)
            val restarted = testAppModel(app)
            assertEquals(Destination.Home, restarted.state.value.destination)
            assertNull(restarted.state.value.mediaId)
            assertNull(restarted.state.value.webPermissionEntryId)
            assertNull(restarted.state.value.audioId)
            assertEquals("", restarted.state.value.editorText)
            assertEquals("准备好表达", restarted.state.value.subtitlePlaceholder)
            assertEquals(16f, restarted.state.value.subtitleSizePercent)
            assertNotNull(restarted.mediaRepository.get(page.id))
            assertTrue(restarted.phrases.value.any { it.id == phrase.id })
        } finally { previous.content.remove(page.id) }
    }

    @Test fun anOldTaskBundleDoesNotReopenTheEditorOnAColdStart() {
        val previous = testAppModel(app)
        previous.content.beginPhrase(null, "常用")
        previous.content.editPhrase { it.copy(text = "旧编辑内容") }
        val bundle = Bundle().apply { putString("quick-phrase-draft", previous.content.savedPhraseDraft()) }
        val controller = Robolectric.buildActivity(MainActivity::class.java).create(bundle)
        try {
            val fresh = ViewModelProvider(controller.get())[AppViewModel::class.java]
            assertEquals(Destination.Home, fresh.state.value.destination)
            assertTrue(fresh.state.value.navigationStack.isEmpty())
            assertFalse(fresh.state.value.quickTextEditing)
            assertNull(fresh.content.phraseDraft.value)
        } finally { controller.destroy() }
    }
}
