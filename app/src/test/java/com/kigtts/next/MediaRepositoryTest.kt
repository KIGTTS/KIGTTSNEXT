package com.kigtts.next

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.media.*
import com.kigtts.next.state.*
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PluginPaths
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaRepositoryTest {
    private lateinit var app: Application; private lateinit var paths: PluginPaths; private lateinit var repository: MediaRepository
    @Before fun setup() { app = ApplicationProvider.getApplicationContext(); paths = PluginPaths(app); repository = MediaRepository(app, paths)
        listOf("experiments", "media-presentation", "quick-phrases").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        repository.entries.value.forEach { repository.remove(it.id) }
    }
    @After fun cleanup() { repository.entries.value.forEach { repository.remove(it.id) }; app.getSharedPreferences("media-presentation",0).edit().clear().commit() }
    @Test fun catalogRoundTripRetainsTypesAndNullableOwnership() {
        val entry = repository.html("测试网页", "<h1>你好</h1>")
        val reloaded = MediaRepository(app, paths); assertEquals(entry, reloaded.get(entry.id)); assertNull(entry.video); assertNull(reloaded.get(entry.id)?.pluginId)
        assertEquals("<h1>你好</h1>", reloaded.file(entry)!!.readText())
        repository.web("页面", "https://example.org/page"); assertEquals(2, repository.entries.value.size)
        assertThrows(IllegalArgumentException::class.java) { repository.web("不安全", "http://example.org") }
    }
    @Test fun presentationScalePersistsPerMediaAndOldCatalogDefaultsToFit() {
        val entry = repository.html("条目", "<p>内容</p>")
        assertEquals(MediaScale.FIT, entry.scale)
        repository.scale(entry.id, MediaScale.FILL)
        assertEquals(MediaScale.FILL, MediaRepository(app, paths).get(entry.id)?.scale)
        val json = entry.json().apply { remove("scale") }
        assertEquals(MediaScale.FIT, MediaEntry.read(json).scale)
    }
    @Test fun pluginHtmlAndReferencesStayInOwnerDataAndCannotReadOtherPlugins() {
        val entry = repository.plugin("test.media", SubtitleContent.Html(html = "<button>插件</button>", allowedActions = listOf("click")))
        val file = repository.file(entry)!!; assertTrue(file.toPath().startsWith(paths.storage("test.media").root.toPath()))
        assertEquals(listOf("click"), entry.actions)
        assertThrows(IllegalArgumentException::class.java) { repository.plugin("test.media", SubtitleContent.Image(PluginResource("file.jpg", "other.plugin"))) }
        assertThrows(IllegalArgumentException::class.java) { repository.plugin("test.media", SubtitleContent.Image(PluginResource("../secret.jpg"))) }
        repository.remove(entry.id); assertTrue("Deleting library reference must not delete plugin-owned resources", file.exists())
    }
    @Test fun applePairingIsOffByDefaultAndIndependentOfAndroidMotionMetadata() {
        val photo = File(app.cacheDir, "photo.jpg").apply { writeBytes(byteArrayOf(1,2,3)) }
        val clip = File(app.cacheDir, "paired.mp4").apply { writeBytes(byteArrayOf(4,5,6)) }
        val entry = repository.import(Uri.fromFile(photo), "image/jpeg")
        assertThrows(IllegalArgumentException::class.java) { repository.pair(entry.id, Uri.fromFile(clip)) }
        val model = testAppModel(app); assertFalse(model.state.value.appleLivePhotos); model.appleLivePhotos(true)
        val paired = repository.pair(entry.id, Uri.fromFile(clip)); assertTrue(paired.appleLive); assertEquals(MediaKind.MOTION_PHOTO, paired.kind)
        assertTrue(testAppModel(app).state.value.appleLivePhotos)
        model.appleLivePhotos(false); assertFalse(testAppModel(app).state.value.appleLivePhotos); assertTrue(repository.file(paired)!!.exists())
    }
    @Test fun legacyQuickPhrasesMigrateAndMediaReferencesSurviveEditingSortingAndRemoval() {
        app.getSharedPreferences("quick-phrases", 0).edit().putString("items", "[{\"text\":\"旧条目\",\"category\":\"常用\"}]").commit()
        val library = QuickTextLibrary(QuickPhraseStore(app)); val old = library.phrases.value.first(); assertNull(old.mediaId)
        val entry = repository.html("附件", "<p>媒体</p>"); library.add("网页", "常用", mediaId = entry.id)
        val media = library.phrases.value.last(); library.edit(media.id, "新名称", "说明", "常用"); library.movePhrase(media.id, old.id)
        val restored = QuickTextLibrary(QuickPhraseStore(app)); assertEquals(entry.id, restored.phrases.value.first().mediaId)
        restored.detachMedia(entry.id); assertNull(restored.phrases.value.first().mediaId); assertEquals("新名称", restored.phrases.value.first().text)
    }
    @Test fun selectionRestoresOnRestartAndRemovingMediaClearsMainAndPhraseReferences() = runBlocking {
        val model = testAppModel(app); val entry = model.mediaRepository.html("附件", "<p>显示</p>")
        model.quickTexts.add("媒体条目", "常用", mediaId = entry.id); model.content.send(model.phrases.value.last())
        assertEquals("HTML", model.context().contentKind); assertEquals(entry.id, testAppModel(app).state.value.mediaId)
        model.content.remove(entry.id); assertNull(model.state.value.mediaId); assertNull(model.phrases.value.last().mediaId)
        assertNull(testAppModel(app).state.value.mediaId)
    }
}
