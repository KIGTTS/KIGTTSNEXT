package com.kigtts.plugin.runtime

import com.kigtts.plugin.api.*
import org.junit.*
import org.junit.Assert.*
import java.io.*
import java.nio.file.Files
import java.util.zip.*

class ResourceAndWireTest {
    @Test fun editorInboxIsScopedButHostConfigurationCannotBeImportedOver() {
        val root = Files.createTempDirectory("editor-inbox").toFile()
        try {
            val storage = PluginStorage(root)
            val path = ResourceImporter.import(storage, "return {}".byteInputStream(), "imports/projects", "main.lua", false)
            assertEquals("imports/projects/main.lua", path)
            assertEquals("return {}", storage.file(path).readText())
            listOf("config/host", "../imports", "modelsevil").forEach { bad ->
                assertThrows(IllegalArgumentException::class.java) { ResourceImporter.import(storage, "secret".byteInputStream(), bad, "x.txt", false) }
            }
        } finally { root.deleteRecursively() }
    }
    private fun archive(path: String): ByteArray = ByteArrayOutputStream().also { result ->
        ZipOutputStream(result).use { it.putNextEntry(ZipEntry(path)); it.write("model".toByteArray()); it.closeEntry() }
    }.toByteArray()
    @Test fun importAndRejectedArchiveLeaveNoFilesOutsidePluginDirectory() {
        val root = Files.createTempDirectory("resources").toFile()
        try {
            val storage = PluginStorage(root)
            ResourceImporter.import(storage, archive("nested/model.onnx").inputStream(), "models", "models.zip", true)
            assertEquals("model", storage.file("models/nested/model.onnx").readText())
            assertThrows(IllegalArgumentException::class.java) { ResourceImporter.import(storage, archive("../escape").inputStream(), "models", "bad.zip", true) }
            assertTrue(storage.file("models/nested/model.onnx").exists())
            assertTrue(storage.temporary.listFiles().orEmpty().isEmpty())
            ResourceImporter.import(storage, "tokens".byteInputStream(), "models", "tokens.txt", false)
            assertEquals("tokens", storage.file("models/tokens.txt").readText())
        } finally { root.deleteRecursively() }
    }
    @Test fun binaryFramesSplitLargePcmAndRejectUnboundedLengths() {
        val audio = PcmAudio(ByteArray(300_000) { (it % 256).toByte() })
        val bytes = ByteArrayOutputStream()
        AudioWire.pcm(DataOutputStream(bytes), audio)
        val input = DataInputStream(bytes.toByteArray().inputStream())
        val restored = ByteArrayOutputStream()
        var frames = 0
        while (AudioWire.tag(input) != null) { restored.write(AudioWire.readPcm(input).bytes); frames++ }
        assertEquals(3, frames); assertArrayEquals(audio.bytes, restored.toByteArray())
        val bad = ByteArrayOutputStream().also { DataOutputStream(it).run { writeInt(16_000); writeInt(1); writeInt(Int.MAX_VALUE) } }
        assertThrows(IllegalArgumentException::class.java) { AudioWire.readPcm(DataInputStream(bad.toByteArray().inputStream())) }
    }
}
