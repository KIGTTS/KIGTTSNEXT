package com.kigtts.next.ui.sketch

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class SketchArtwork(val id: String, val createdAt: Long, val width: Int, val height: Int, val mediaUri: String)

/** The drawing album is intentionally independent of polaroids and third-party picture libraries. */
class SketchAlbumRepository internal constructor(context: Context,
    private val publication: SketchPublication = SketchMediaPublisher(context)) {
    private val root = File(context.getExternalFilesDir(null) ?: context.filesDir, "appdata/Kigtts/sketch")
    private val works = File(root, "works")
    private val draft = File(root, "draft.json")
    private val identifier = Regex("[a-f0-9-]{36}")

    @Synchronized fun list(): List<SketchArtwork> = works.listFiles().orEmpty().filter { it.isDirectory && identifier.matches(it.name) }
        .mapNotNull { directory -> runCatching { metadata(directory) }.getOrNull() }.sortedByDescending { it.createdAt }

    @Synchronized fun save(document: SketchDocument): SketchArtwork {
        check(works.isDirectory || works.mkdirs()) { "无法创建作品相册" }
        // Validate the vector before publishing either representation.
        val encoded = SketchDocumentCodec.encode(document); SketchDocumentCodec.decode(encoded)
        val id = UUID.randomUUID().toString(); val directory = File(works, ".pending-$id")
        check(directory.mkdir()) { "无法保存画板" }
        var uri: Uri? = null
        try {
            val image = File(directory, "image.png")
            val bitmap = renderSketchDocument(document)
            val width = bitmap.width; val height = bitmap.height
            try { image.outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) { "无法编码画板" } } }
            finally { bitmap.recycle() }
            File(directory, "drawing.json").writeText(encoded, Charsets.UTF_8)
            uri = publication.publish(image, "KIGTTS-画板-$id.png", width, height)
            val artwork = SketchArtwork(id, System.currentTimeMillis(), width, height, uri.toString())
            File(directory, "work.json").writeText(JSONObject().apply {
                put("id", id); put("createdAt", artwork.createdAt); put("width", width); put("height", height); put("mediaUri", artwork.mediaUri)
            }.toString(), Charsets.UTF_8)
            check(directory.renameTo(File(works, id))) { "无法完成本地保存" }
            return artwork
        } catch (failure: Exception) {
            uri?.let { runCatching { publication.remove(it) } }
            directory.deleteRecursively()
            throw failure
        }
    }

    @Synchronized fun load(id: String): SketchDocument = SketchDocumentCodec.decode(File(owned(id), "drawing.json").readText(Charsets.UTF_8))
    @Synchronized fun imageFile(id: String): File = File(owned(id), "image.png")

    @Synchronized fun remove(id: String) {
        val directory = owned(id); val artwork = metadata(directory)
        // Only the URI recorded by our completed save transaction is eligible for deletion.
        publication.remove(Uri.parse(artwork.mediaUri))
        check(directory.deleteRecursively()) { "无法移除本地作品" }
    }

    @Synchronized fun saveDraft(document: SketchDocument) {
        check(root.isDirectory || root.mkdirs()) { "无法保存画板草稿" }
        val stage = File(root, ".draft-${UUID.randomUUID()}.tmp")
        try {
            val encoded = SketchDocumentCodec.encode(document)
            SketchDocumentCodec.decode(encoded)
            stage.outputStream().use { output -> output.write(encoded.toByteArray(Charsets.UTF_8)); output.fd.sync() }
            Files.move(stage.toPath(), draft.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { stage.delete() }
    }
    @Synchronized fun loadDraft(): SketchDocument? {
        val legacyBackup = File(root, "draft.json.bak")
        if (legacyBackup.isFile) Files.move(legacyBackup.toPath(), draft.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        return if (draft.isFile) draft.inputStream().use { SketchDocumentCodec.decode(it.readBytes().toString(Charsets.UTF_8)) } else null
    }

    private fun owned(id: String): File {
        require(identifier.matches(id)) { "作品不存在" }
        val file = File(works, id).canonicalFile
        require(file.parentFile == works.canonicalFile && file.isDirectory) { "作品不存在" }
        return file
    }
    private fun metadata(directory: File): SketchArtwork {
        val json = JSONObject(File(directory, "work.json").readText(Charsets.UTF_8))
        check(json.getString("id") == directory.name && File(directory, "drawing.json").isFile && File(directory, "image.png").isFile)
        return SketchArtwork(json.getString("id"), json.getLong("createdAt"), json.getInt("width"), json.getInt("height"), json.getString("mediaUri"))
    }
}
