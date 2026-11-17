package com.kigtts.next.ui.cards.camera

import android.content.Context
import android.net.Uri
import com.kigtts.next.media.color.CubeLut
import com.kigtts.next.i18n.uiCopy
import java.io.File
import java.security.MessageDigest

internal data class CameraLutChoice(val id: String, val label: String, val lut: CubeLut)
internal class CameraLutLibrary(private val context: Context) {
    private val resolver = context.contentResolver
    private val directory = File(context.filesDir, "camera-luts")
    fun choices(): List<CameraLutChoice> = directory.listFiles().orEmpty().filter { it.extension == "cube" }.sortedBy { it.name }.take(32).mapNotNull { file ->
        runCatching { val lut = file.reader(Charsets.UTF_8).use { CubeLut.read(it) }; CameraLutChoice(file.nameWithoutExtension, lut.title, lut) }.getOrNull()
    }
    fun import(uri: Uri): CameraLutChoice {
        directory.mkdirs()
        val stage = File.createTempFile("import-", ".tmp", directory)
        try {
            requireNotNull(resolver.openInputStream(uri)).use { input -> stage.outputStream().use { output ->
                val buffer = ByteArray(8192); var bytes = 0L
                while (true) { val count = input.read(buffer); if (count < 0) break; bytes += count; require(bytes <= CubeLut.MAX_BYTES) { context.uiCopy("camera.lut_too_large") }; output.write(buffer, 0, count) }
            } }
            val lut = stage.reader(Charsets.UTF_8).use { CubeLut.read(it) }
            val hash = MessageDigest.getInstance("SHA-256").digest(stage.readBytes()).joinToString("") { "%02x".format(it) }
            val target = File(directory, "$hash.cube")
            require(target.exists() || directory.listFiles().orEmpty().count { it.extension == "cube" } < 32) { context.uiCopy("camera.lut_limit") }
            if (!target.exists()) java.nio.file.Files.move(stage.toPath(), target.toPath())
            return CameraLutChoice(hash, lut.title, lut)
        } finally { stage.delete() }
    }
}
