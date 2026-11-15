package com.kigtts.next

import android.content.Intent
import android.net.Uri
import com.kigtts.next.state.ExternalFileType.*
import com.kigtts.next.state.ExternalFileTypes
import com.kigtts.next.state.ExternalFileOpen
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExternalFileTypesTest {
    @get:Rule val folder = TemporaryFolder()
    private fun archive(vararg entries: Pair<String, String>): File = folder.newFile().also { file ->
        ZipOutputStream(file.outputStream()).use { zip -> entries.forEach { (name, value) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray()); zip.closeEntry()
        } }
    }
    @Test fun recognizesActualExportExtensionsWithoutMimeGuessing() {
        assertEquals(QUICK_TEXT_BACKUP, ExternalFileTypes.detect("phrases.kigcards"))
        listOf("kigtpk", "kigspk", "kigcard", "kigconfig").forEach { assertEquals(LEGACY_BACKUP, ExternalFileTypes.detect("old.$it")) }
        assertEquals(PIPER_VOICE, ExternalFileTypes.detect("流萤.KIGVPK"))
        assertEquals(PLUGIN, ExternalFileTypes.detect("engine.kigplugin"))
        assertEquals(UNKNOWN, ExternalFileTypes.detect("backup.zip"))
    }
    @Test fun genericDownloadsRouteByArchiveContents() {
        assertEquals(PIPER_VOICE, ExternalFileTypes.inspect(archive("nested/zh.onnx" to "model", "nested/zh.onnx.json" to "{}"), "download.bin"))
        assertEquals(QUICK_TEXT_BACKUP, ExternalFileTypes.inspect(archive("cards.json" to "{}"), "download.zip"))
        assertEquals(PLUGIN, ExternalFileTypes.inspect(archive("plugin.json" to "{}"), "download.zip"))
        assertEquals(LEGACY_BACKUP, ExternalFileTypes.inspect(archive("manifest.json" to """{"type":"soundboard"}"""), "download.zip"))
        assertEquals(UNKNOWN, ExternalFileTypes.inspect(archive("notes.txt" to "hello"), "download.zip"))
    }
    @Test fun unsupportedOrEmptyMetadataNeverBecomesABackup() {
        val file = folder.newFile().apply { writeText("""{"type":"unrelated"}""") }
        assertEquals(UNKNOWN, ExternalFileTypes.inspect(file, "data.json"))
        file.writeText("not a backup")
        assertEquals(UNKNOWN, ExternalFileTypes.inspect(file, "data.json"))
    }
    @Test fun routesVoicePacksByPayloadAndEngineRatherThanExtension() {
        val legacy = """{"formatVersion":1,"name":"参考声音","referenceText":"你好"}"""
        val qwen = """{"formatVersion":1,"name":"参考声音","referenceText":"你好","engine":"engine.qwen3-clone"}"""
        assertEquals(SHARED_CLONE_VOICE, ExternalFileTypes.inspect(archive("voice.json" to legacy, "reference.wav" to "audio"), "renamed.kigvpk"))
        assertEquals(QWEN_VOICE, ExternalFileTypes.inspect(archive("voice.json" to qwen, "reference.wav" to "audio"), "voice.zip"))
        assertEquals(PIPER_VOICE, ExternalFileTypes.inspect(archive("wrap/manifest.json" to
            """{"files":{"model":"tts\\voice.onnx","config":"tts/config.json"}}""",
            "wrap/tts/voice.onnx" to "model", "wrap/tts/config.json" to "{}"), "legacy.kigvpk"))
        assertEquals(UNKNOWN, ExternalFileTypes.inspect(archive("model.onnx" to "model"), "model.kigvpk"))
        assertEquals(UNKNOWN, ExternalFileTypes.inspect(archive("manifest.json" to """{"files":{"model":"missing.onnx"}}"""), "invalid.kigvpk"))
    }
    @Test fun remoteLinksAndPlainTextSharesAreNotOpenedAsFiles() {
        assertNull(ExternalFileOpen.localUri(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/backup.kigconfig"))))
        assertNull(ExternalFileOpen.localUri(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "content://files/item")))
        val uri = Uri.parse("content://files/voice")
        assertEquals(uri, ExternalFileOpen.localUri(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uri)))
        assertEquals(uri, ExternalFileOpen.localUri(Intent(Intent.ACTION_VIEW, uri)))
    }
}
