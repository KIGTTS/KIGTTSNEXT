package com.kigtts.engines.piper

import com.kigtts.plugin.api.PluginStorage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class PiperVoicesTest {
    @get:Rule val files = TemporaryFolder()
    private val config = """{"audio":{"sample_rate":16000},"phoneme_type":"espeak","phoneme_id_map":{"_":[0],"^":[1],"$":[2],"a":[3]},"espeak":{"voice":"en-us"},"language":{"code":"en_US"},"speaker_id_map":{"first":0,"second":1}}"""
    private fun zip(vararg entries: Pair<String, String>): File = files.newFile().also { file ->
        ZipOutputStream(file.outputStream()).use { zip -> entries.forEach { (path, text) ->
            zip.putNextEntry(ZipEntry(path)); zip.write(text.toByteArray()); zip.closeEntry()
        } }
    }
    private fun archive() = zip("nested/model.onnx" to "model", "nested/model.onnx.json" to config)
    @Test fun standardPackImportSelectRenameAndDeletePreserveScopedData() {
        val storage = PluginStorage(files.newFolder())
        val voices = PiperVoices(storage)
        val first = voices.import(archive())
        assertTrue(first.name.startsWith("Piper 语音 1"))
        assertEquals(2, voices.list().size)
        assertEquals(first.id, voices.selected)
        val second = voices.list().first { it.id != first.id }
        voices.choose(second.id)
        assertEquals(second.id, PiperVoices(storage).selected)
        assertEquals(1, voices.resolve(second.id).second)
        assertEquals(16000, voices.resolve(first.id).first.sampleRate)
        voices.rename(first.id, "新语音包")
        assertTrue(voices.list().all { it.name.startsWith("新语音包") })
        voices.delete(second.id)
        assertTrue(voices.list().isEmpty())
        assertNull(PiperVoices(storage).selected)
    }
    @Test fun legacyManifestAndMetadataAreRetained() {
        val voices = PiperVoices(PluginStorage(files.newFolder()))
        val imported = voices.import(zip("manifest.json" to """{"files":{"model":"voice.onnx","config":"config.json"},"sample_rate":22050}""",
            "meta.json" to """{"name":"旧声音","remark":"旧备注"}""", "voice.onnx" to "model", "config.json" to config))
        assertTrue(imported.name.startsWith("旧声音"))
        assertEquals("旧备注", imported.description)
        assertEquals(22050, voices.resolve(imported.id).first.sampleRate)
    }
    @Test fun invalidArchiveCannotEscapeOrReplaceInstalledVoice() {
        val storage = PluginStorage(files.newFolder())
        val voices = PiperVoices(storage)
        val good = voices.import(archive())
        for (bad in listOf(zip("../outside" to "bad"), zip("voice.onnx" to "bad", "voice.onnx.json" to "{}"),
            zip("voice.onnx" to "model", "voice.onnx.json" to config, "meta.json" to "invalid"))) {
            assertTrue(runCatching { voices.import(bad) }.isFailure)
            assertEquals(2, voices.list().size)
            assertEquals(good.id, voices.selected)
            assertTrue(storage.temporary.listFiles().orEmpty().isEmpty())
        }
    }
    @Test fun manifestModelPathCannotEscapeVoicePack() {
        val root = files.newFolder()
        File(root, "manifest.json").writeText("""{"files":{"model":"../outside.onnx","config":"config.json"}}""")
        assertTrue(runCatching { PiperPack.read(root) }.isFailure)
    }
}
