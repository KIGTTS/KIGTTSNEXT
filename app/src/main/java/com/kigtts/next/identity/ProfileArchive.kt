package com.kigtts.next.identity

import android.graphics.BitmapFactory
import com.kigtts.next.cards.StickerRepository
import com.kigtts.next.sharing.faceAvatarData
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Base64
import java.util.UUID

/** MHTML contains a browser document and a separate, inert KIGTTS snapshot. */
internal object ProfileArchive {
    const val MIME = "multipart/related"
    const val MAX_BYTES = 64L * 1024 * 1024
    private const val MARKER = "X-KIGTTS-Profile: 1"
    private const val DATA = "application/vnd.kigtts.profile+json"
    private const val ORIGIN = "https://kigtts.invalid/"
    private const val MAX_IMAGE = 2L * 1024 * 1024
    private const val MAX_JSON = 384L * 1024
    private val boundaryPattern = Regex("kigtts-[a-f0-9-]{36}")
    private val imageName = Regex("(cover|tile-([0-9]|1[0-9]|2[0-3]))\\.jpg")
    private val ascii = Charsets.US_ASCII

    fun write(identity: IdentityCard, stickers: StickerRepository, directory: File): File {
        directory.mkdirs()
        val cover = exportIdentityCard(identity, stickers, directory)
        val images = identity.tiles.take(24).mapIndexedNotNull { index, tile ->
            if (tile.kind != ProfileTileKind.IMAGE) return@mapIndexedNotNull null
            val source = profileImageFile(stickers, tile.image) ?: return@mapIndexedNotNull null
            val output = File(directory, "tile-$index.jpg")
            output.takeIf { writeProfileJpeg(source, it) && it.length() <= MAX_IMAGE }?.let { index to it }
        }.toMap()
        val coverJpeg = File(directory, "cover.jpg")
        check(writeProfileJpeg(cover, coverJpeg) && coverJpeg.length() <= MAX_IMAGE) { "未能生成名片封面" }
        val profile = PublicIdentityProfile.from(identity.publicJson()).let {
            it.copy(avatar = identity.avatar?.let(stickers::imageFile)?.let(::faceAvatarData),
                tiles = it.tiles.map { tile -> tile.copy(image = "") })
        }.json().put("identityId", identity.id)
        val index = JSONObject()
        identity.tiles.take(24).forEachIndexed { i, tile -> images[i]?.let { index.put(it.name, tile.id) } }
        val metadata = JSONObject().put("profile", profile).put("images", index).toString().toByteArray(Charsets.UTF_8)
        require(metadata.size <= MAX_JSON) { "名片内容过多" }
        val html = profileArchiveHtml(identity, stickers.context, images.keys).toByteArray(Charsets.UTF_8)
        return File(directory, "profile.mhtml").also { file ->
            file.outputStream().buffered().use { output ->
                val boundary = "kigtts-${UUID.randomUUID()}"
                output.ascii("MIME-Version: 1.0\r\n$MARKER\r\nContent-Type: $MIME; type=\"text/html\"; boundary=\"$boundary\"\r\n\r\n")
                output.part(boundary, "text/html; charset=UTF-8", "profile.html", html.inputStream())
                output.part(boundary, DATA, "profile.json", metadata.inputStream())
                (listOf(coverJpeg) + images.values).forEach { image ->
                    output.part(boundary, "image/jpeg", image.name, image.inputStream())
                }
                output.ascii("--$boundary--\r\n")
            }
            check(file.length() <= MAX_BYTES) { "名片归档过大" }
        }
    }

    fun recognizes(file: File): Boolean = runCatching {
        file.inputStream().buffered().use { it.header().contains(MARKER) }
    }.getOrDefault(false)

    /** Never run/render HTML from the archive. Import only typed, bounded snapshot parts. */
    fun restore(file: File, directory: File, favorites: ContactFavorites): SavedContact {
        require(file.length() in 1..MAX_BYTES) { "名片文件超出大小限制" }
        directory.mkdirs()
        return file.inputStream().buffered().use { input ->
            val header = input.header()
            val boundary = header.singleOrNull { it.startsWith("Content-Type: $MIME;") }
                ?.substringAfter("boundary=\"", "")?.substringBefore('"').orEmpty()
            require(MARKER in header && boundaryPattern.matches(boundary)) { "只能导入 KIGTTS 导出的 MHTML 名片" }
            val delimiter = "--$boundary"
            require(input.line() == delimiter) { "名片归档格式不正确" }
            var profile: JSONObject? = null
            val files = mutableMapOf<String, File>()
            val data = File(directory, "metadata.json")
            repeat(28) {
                val headers = input.header()
                val type = headers.singleOrNull { it.startsWith("Content-Type: ") }?.substringAfter(": ")
                val name = headers.singleOrNull { it.startsWith("Content-Location: $ORIGIN") }
                    ?.removePrefix("Content-Location: $ORIGIN").orEmpty()
                val base64 = "Content-Transfer-Encoding: base64" in headers
                val target = when {
                    base64 && type == DATA && name == "profile.json" && profile == null -> data
                    base64 && type == "image/jpeg" && imageName.matches(name) && name !in files -> File(directory, name)
                    else -> null
                }
                val next = input.body(delimiter, target, if (target == data) MAX_JSON else MAX_IMAGE)
                if (target == data && target.isFile) {
                    profile = OfflineProfileData.parse(target.readText(Charsets.UTF_8))
                    target.delete()
                } else if (target != null && target.isFile && isProfileJpeg(target)) files[name] = target
                if (next == "$delimiter--") {
                    val snapshot = requireNotNull(profile?.optJSONObject("profile")?.let(OfflineProfileData::snapshot)) {
                        "没有可恢复的名片数据"
                    }
                    val tiles = profileTiles(snapshot)
                    val index = profile?.optJSONObject("images")
                    val photos = files.mapNotNull { (part, image) ->
                        val id = (index?.opt(part) as? String)?.takeIf(String::isNotBlank)
                        id?.takeIf { key -> tiles.any { it.id == key && it.kind == ProfileTileKind.IMAGE } }?.let { it to image }
                    }.toMap()
                    snapshot.put("tiles", profileTileJson(tiles.map { tile -> tile.copy(image = "") }))
                    if (!validAvatar(snapshot.optString("avatar"))) snapshot.remove("avatar")
                    return@use favorites.save(snapshot, files["cover.jpg"], photos)
                }
            }
            error("名片归档包含过多内容")
        }
    }

    private fun OutputStream.ascii(text: String) = write(text.toByteArray(ascii))
    private fun OutputStream.part(boundary: String, mime: String, name: String, content: InputStream) {
        ascii("--$boundary\r\nContent-Type: $mime\r\nContent-Location: $ORIGIN$name\r\nContent-Transfer-Encoding: base64\r\n\r\n")
        val destination = this
        val open = object : OutputStream() {
            override fun write(b: Int) = destination.write(b)
            override fun write(b: ByteArray, off: Int, len: Int) = destination.write(b, off, len)
        }
        content.use { source -> Base64.getMimeEncoder(76, byteArrayOf(13, 10)).wrap(open).use { source.copyTo(it) } }
        ascii("\r\n")
    }

    private fun BufferedInputStream.header(): List<String> = buildList {
        repeat(12) {
            val line = line()
            if (line.isEmpty()) return@buildList
            add(line)
        }
        error("名片归档头部过长")
    }

    private fun BufferedInputStream.line(): String {
        val result = StringBuilder(96)
        repeat(1024) {
            val byte = read()
            require(byte in 0..127) { "名片归档未完整读取" }
            if (byte == 10) return result.toString().trimEnd('\r')
            result.append(byte.toChar())
        }
        error("名片归档数据行过长")
    }

    private fun BufferedInputStream.body(delimiter: String, file: File?, cap: Long): String {
        var total = 0L
        var valid = file != null
        val sink = file?.outputStream()?.buffered()
        try {
            while (true) {
                val line = line()
                if (line == delimiter || line == "$delimiter--") return line
                if (!valid || line.isEmpty()) continue
                val bytes = runCatching { Base64.getDecoder().decode(line) }.getOrNull()
                if (bytes == null || total + bytes.size > cap) valid = false
                else { sink?.write(bytes); total += bytes.size }
            }
        } finally {
            sink?.close()
            if (!valid || total == 0L) file?.delete()
        }
    }

    private fun isProfileJpeg(file: File): Boolean {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, opts)
        return opts.outMimeType == "image/jpeg" && opts.outWidth in 1..1600 && opts.outHeight in 1..1600
    }

    private fun validAvatar(value: String): Boolean {
        if (!value.matches(Regex("[A-Za-z0-9+/=]{4,65000}"))) return false
        val data = runCatching { Base64.getDecoder().decode(value) }.getOrNull() ?: return false
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, opts)
        return opts.outMimeType == "image/jpeg" && opts.outWidth in 1..128 && opts.outHeight in 1..128
    }
}
