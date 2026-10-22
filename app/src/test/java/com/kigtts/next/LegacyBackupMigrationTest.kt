@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.kigtts.next

import android.app.Application
import android.graphics.Bitmap
import androidx.datastore.preferences.core.*
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.cards.*
import com.kigtts.next.legacy.*
import com.kigtts.next.media.MediaRepository
import com.kigtts.next.state.*
import com.kigtts.plugin.runtime.PluginPaths
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LegacyBackupMigrationTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val root get() = File(app.cacheDir, "legacy-test").apply { mkdirs() }
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()); app.getSharedPreferences("quick-phrases", 0).edit().clear().commit() }
    @After fun after() { Dispatchers.resetMain() }
    private fun zip(vararg files: Pair<String, ByteArray>): File = File(root, "input-${java.util.UUID.randomUUID()}").apply {
        ZipOutputStream(outputStream()).use { out -> files.forEach { (name, bytes) -> out.putNextEntry(ZipEntry(name)); out.write(bytes); out.closeEntry() } }
    }
    private fun read(source: File) = LegacyArchive.read(source, File(root, "stage-${java.util.UUID.randomUUID()}"))
    private fun json(text: String) = text.toByteArray(Charsets.UTF_8)
    private fun image(): ByteArray = Bitmap.createBitmap(4, 8, Bitmap.Config.ARGB_8888).let { bitmap ->
        java.io.ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); bitmap.recycle() }.toByteArray()
    }
    private fun repositories(): Triple<QuickTextLibrary, MediaRepository, CardRepository> {
        val paths = PluginPaths(app); val media = MediaRepository(app, paths)
        val stickers = StickerRepository(app, paths, media)
        stickers.cards.value.takeIf { it.isNotEmpty() }?.let { stickers.apply(listOf(com.kigtts.plugin.api.StickerEdit.RemoveCards(it.map { card -> card.id }))) }
        return Triple(QuickTextLibrary(QuickPhraseStore(app)), media, CardRepository(app, stickers))
    }
    @Test fun quickTextPackagePreservesOrderColorAndStableIdentityWithoutWritingBeforeConfirmation() = runBlocking {
        val archive = zip("preset.json" to json("""{"type":"quick_subtitle","version":1,"groups":[{"id":7,"title":"交流","icon":"chat","items":["你好🌏","第二条"],"itemColors":[-65536,null]}]}"""))
        val (library, media, cards) = repositories(); val before = library.phrases.value
        val plan = read(archive)
        assertEquals(before, library.phrases.value)
        val importer = LegacyMigration(library, media, cards)
        assertEquals(2 to 0, importer.apply(plan))
        val group = library.phrases.value.filter { it.category == "交流" }
        assertEquals(listOf("你好🌏", "第二条"), group.map { it.text }); assertEquals(0xffff0000L, group.first().color)
        library.editCategory("交流", "改过名字", CategoryIcon.PERSON)
        assertEquals(0 to 0, importer.apply(read(archive)))
        assertEquals(before.size + 2, library.phrases.value.size)
    }
    @Test fun audioPackageBecomesAudioCardsWithOriginalBytesAndTrimMetadata() = runBlocking {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val archive = zip("preset.json" to json("""{"type":"soundboard","version":1,"groups":[{"id":3,"title":"音效","items":[{"id":9,"title":"提示音","audioFile":"audio/a.wav","trimStartMs":200,"trimEndMs":800}]}]}"""), "audio/a.wav" to bytes)
        val (library, media, cards) = repositories()
        assertEquals(1 to 0, LegacyMigration(library, media, cards).apply(read(archive)))
        val item = library.phrases.value.last()
        assertEquals(QuickCardType.AUDIO, item.type); assertEquals("200", item.metadata["legacy.trimStartMs"])
        assertArrayEquals(bytes, media.file(requireNotNull(media.get(item.mediaId)))!!.readBytes())
    }
    @Test fun portraitAndLandscapeSplitQrStaysCenteredAndMissingPictureUsesBackgroundColor() = runBlocking {
        val archive = zip("manifest.json" to json("""{"type":"kigtts_quick_cards","version":1,"cards":[{"id":5,"type":"qr","title":"二维码","link":"https://example.org/你好","portraitImage":"images/p.png","landscapeImage":"images/l.png"},{"id":6,"type":"text","title":"色卡","themeColor":"#123456"}]}"""),
            "images/p.png" to image(), "images/l.png" to image())
        val (library, media, cards) = repositories(); val plan = read(archive)
        assertEquals(3, plan.cards.size)
        val importer = LegacyMigration(library, media, cards)
        assertEquals(0 to 3, importer.apply(plan)); assertEquals(0 to 0, importer.apply(plan))
        val photos = cards.cardsInAlbum(LegacyMigration.ALBUM)
        assertEquals(3, photos.size); assertEquals(2, photos.count { it.layers.size == 1 })
        assertTrue(photos.filter { it.layers.isNotEmpty() }.all { it.layers.single().x == .5 && it.layers.single().text.contains("你好") })
        val solid = photos.single { it.title == "色卡" }
        val bitmap = android.graphics.BitmapFactory.decodeFile(cards.stickers.imageFile(solid.imageId!!)!!.path)
        assertEquals(0xff123456.toInt(), bitmap.getPixel(0, 0)); bitmap.recycle()
    }
    @Test fun fullBackupRebasesOnlyKnownResourcesAndDoesNotReadOldAbsolutePaths() {
        val configuration = """{"cards":[{"id":1,"title":"原卡","type":"image","portraitImagePath":"/data/user/0/com.lhtstudio.kigtts.app/files/quick_cards/p.png"}]}"""
        val preferences = JSONObject().put("entries", org.json.JSONArray().put(JSONObject().put("name", "quick_card_config").put("type", "string").put("value", configuration)))
        val archive = zip("manifest.json" to json("""{"type":"kigtts_config_backup","version":2}"""), "preferences.json" to json(preferences.toString()), "resources/quick_cards/p.png" to image())
        assertNotNull(read(archive).cards.single().image)
    }
    @Test fun malformedOrMissingResourcesCannotPartiallyChangeLibraries() {
        assertTrue(runCatching { legacyJson("{\"nested\":" + "[".repeat(5000) + "0" + "]".repeat(5000) + "}") }.isFailure)
        val attacks = listOf(zip("../escaped" to byteArrayOf(0), "preset.json" to json("{}")),
            zip("preset.json" to json("""{"type":"soundboard","groups":[{"items":[{"title":"缺文件","audioFile":"/etc/passwd"}]}]}""")),
            zip("preset.json" to json("""{"type":"quick_subtitle","version":99,"groups":[]}""")))
        attacks.forEach { source -> assertTrue(runCatching { read(source) }.isFailure) }
        assertFalse(File(root.parentFile, "escaped").exists())
    }
    @Test fun originalDataStoreIsReadFromSnapshotAndOriginalBytesRemainIdentical() = runBlocking {
        val source = File(app.filesDir, "datastore/user_prefs.preferences_pb").apply { parentFile!!.mkdirs() }
        val job = SupervisorJob(); val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO), produceFile = { source })
        store.edit { it[stringPreferencesKey("quick_subtitle_config")] = """{"groups":[{"id":2,"title":"旧库","items":["留下原件"]}]}""" }
        job.cancelAndJoin(); val original = source.readBytes()
        try { val plan = OriginalPreferences.read(app, source)
            assertEquals("留下原件", plan.groups.single().items.single().text)
            assertArrayEquals(original, source.readBytes())
        } finally { source.delete() }
    }
}
