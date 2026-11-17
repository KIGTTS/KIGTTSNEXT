@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.kigtts.next

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.state.*
import com.kigtts.next.media.MediaEntry
import com.kigtts.next.media.MediaKind
import com.kigtts.plugin.api.QuickTextReply
import com.kigtts.plugin.runtime.PluginPaths
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuickTextBackupTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    @Before fun before() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app.getSharedPreferences("quick-phrases", 0).edit().clear().commit()
    }
    @After fun after() { Dispatchers.resetMain() }
    @Test fun nativeExportAndImportWorkWithoutAgentAndWaitForReview() = runBlocking {
        val model = AppViewModel(app, emptyList())
        model.quickTexts.add("备份中的完整文字\n第二行", model.quickTexts.categories.value.first())
        val before = model.phrases.value.toList()
        val backup = QuickTextBackup(model)
        val archive = backup.prepareExport()
        assertTrue(archive.length() > 0)
        val target = File(app.cacheDir, "native-backup-copy.kigcards")
        backup.saveExport(app.contentResolver, Uri.fromFile(target), "exports/${archive.name}")
        assertArrayEquals(archive.readBytes(), target.readBytes())
        val reply = backup.prepareImport(app.contentResolver, Uri.fromFile(target))
        assertTrue(reply.toString(), reply is QuickTextReply.Prepared)
        assertEquals("No writes before native review", before, model.phrases.value)
        val preview = (reply as QuickTextReply.Prepared).value
        assertTrue(model.quickTextPlugins.approve(preview.token) is QuickTextReply.Applied)
        assertEquals(before.size * 2, model.phrases.value.size)
        assertEquals(2, model.phrases.value.count { it.text == "备份中的完整文字\n第二行" })
        assertTrue(target.exists())
    }
    @Test fun invalidArchiveLeavesTheLibraryUntouchedAndHostIdentityCannotBeInstalled() = runBlocking {
        val model = AppViewModel(app, emptyList())
        val before = model.phrases.value.toList()
        val invalid = File(app.cacheDir, "bad-backup.kigcards").apply { writeText("not an archive") }
        val result = QuickTextBackup(model).prepareImport(app.contentResolver, Uri.fromFile(invalid))
        assertTrue(result is QuickTextReply.Failure)
        assertEquals(before, model.phrases.value)
        assertThrows(IllegalArgumentException::class.java) { model.pluginManager.paths.packageDirectory(PluginPaths.HOST_BACKUP_ID) }
        assertTrue(model.pluginManager.paths.storage(PluginPaths.HOST_BACKUP_ID).root.isDirectory)
    }
    @Test fun selectedGroupsAndUnsavedDraftExportWithoutMutatingTheLibrary() = runBlocking {
        val model = AppViewModel(app, emptyList())
        val groups = model.quickTexts.categories.value
        val chosen = groups.first()
        model.quickTexts.add("仅选中的分组", chosen)
        val backup = QuickTextBackup(model)
        val before = model.phrases.value.toList()
        val groupId = requireNotNull(model.quickTexts.categoryIds.value[chosen])
        val archive = backup.prepareExport(listOf(groupId))
        val selection = QuickArchiveSelection.read(archive)
        assertEquals(listOf(groupId), selection.groups.map { it.id })
        assertTrue(selection.entries.all { it.groupId == groupId })
        val draft = PhraseDraft("test", null, "尚未保存的文字", "草稿", chosen, null)
        val exported = QuickArchiveSelection.read(backup.exportDraft(draft))
        assertEquals("尚未保存的文字", exported.entries.single().text)
        assertEquals(before, model.phrases.value)
    }

    @Test fun sharingAndRestoringASelectedGroupKeepsItsEmbeddedMedia() = runBlocking {
        val model = AppViewModel(app, emptyList())
        val owner = PluginPaths.HOST_BACKUP_ID
        val path = "resources/backup-fixture.png"
        val bytes = byteArrayOf(3, 2, 1, 0)
        model.pluginManager.paths.storage(owner).file(path).apply { parentFile!!.mkdirs(); writeBytes(bytes) }
        val media = model.mediaRepository.registerPluginMedia(MediaEntry("backup-fixture", "配图", MediaKind.IMAGE, path, "image/png", pluginId = owner))
        val category = model.categories.value.first()
        val phrase = QuickPhrase("带资源的快捷文本", category, mediaId = media.id, type = QuickCardType.MEDIA)
        assertTrue(model.quickTexts.savePhrase(phrase))
        val backup = QuickTextBackup(model)
        val group = requireNotNull(model.quickTexts.categoryIds.value[category])
        val shared = backup.prepareExport(entryIds = listOf(phrase.id))
        val bundle = QuickArchiveSelection.read(shared)
        java.util.zip.ZipFile(shared).use { zip ->
            assertArrayEquals(bytes, zip.getInputStream(zip.getEntry(bundle.media.single().primary)).use { it.readBytes() })
        }
        val archive = backup.prepareExport(groupIds = listOf(group))
        val selection = backup.inspectImport(app.contentResolver, Uri.fromFile(archive))
        val prepared = backup.importSelected(selection, setOf(group)) as QuickTextReply.Prepared
        assertTrue(model.quickTextPlugins.approve(prepared.value.token) is QuickTextReply.Applied)
        val restored = model.phrases.value.single { it.text == phrase.text && it.id != phrase.id }
        val restoredMedia = requireNotNull(model.mediaRepository.get(restored.mediaId))
        assertArrayEquals(bytes, model.mediaRepository.file(restoredMedia)!!.readBytes())
    }
}
