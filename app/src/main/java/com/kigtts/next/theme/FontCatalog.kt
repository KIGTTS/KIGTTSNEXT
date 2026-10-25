package com.kigtts.next.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import com.caverock.androidsvg.SVG
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class CatalogFont(val id: String, val name: String, val url: String, val sha256: String, val bytes: Long,
    val preview: String, val previewSha256: String, val license: String)

class FontCatalog(private val context: Context) {
    private val cache = File(context.cacheDir, "font-catalog").apply { mkdirs() }
    fun list(): List<CatalogFont> {
        val cached = File(cache, "index.json")
        val bytes = runCatching { fetch(BASE + "index.json", 256 * 1024).also { parse(it.toString(Charsets.UTF_8)); cached.writeBytes(it) } }
            .getOrElse { if (cached.isFile) cached.readBytes() else throw it }
        return parse(bytes.toString(Charsets.UTF_8))
    }
    fun preview(font: CatalogFont): Bitmap {
        val file = asset(font.preview, font.previewSha256, 1024 * 1024)
        val svg = file.inputStream().use(SVG::getFromInputStream)
        return Bitmap.createBitmap(880, 160, Bitmap.Config.ARGB_8888).also {
            svg.setDocumentWidth(880f); svg.setDocumentHeight(160f); svg.renderToCanvas(Canvas(it))
        }
    }
    fun install(font: CatalogFont): String {
        val file = asset(font.url, font.sha256, AppearanceAssets.MAX_FONT)
        require(file.length() == font.bytes) { "字体下载不完整，请重试" }
        return AppearanceAssets(context).importFont(Uri.fromFile(file))
    }
    private fun asset(url: String, hash: String, limit: Int): File {
        require(hash.matches(Regex("[a-f0-9]{64}")))
        val file = File(cache, hash)
        if (file.isFile && AppearanceAssets.digest(file.readBytes()) == hash) return file
        val bytes = fetch(url, limit)
        require(AppearanceAssets.digest(bytes) == hash) { "下载校验失败，请重试" }
        file.writeBytes(bytes)
        return file
    }
    companion object {
        const val BASE = "https://modelscope.cn/datasets/LHTSTUDIO/KIGTTS-Fonts/resolve/master/"
        fun parse(text: String): List<CatalogFont> {
            val root = JSONObject(text)
            require(root.getInt("schemaVersion") == 1)
            val fonts = root.getJSONArray("fonts"); require(fonts.length() <= 200)
            return (0 until fonts.length()).map { i -> fonts.getJSONObject(i).let {
                CatalogFont(it.getString("id"), it.getString("name"), address(it.getString("url")), it.getString("sha256"),
                    it.getLong("bytes").also { bytes -> require(bytes in 1..AppearanceAssets.MAX_FONT.toLong()) },
                    address(it.getString("preview")), it.getString("previewSha256"), it.getString("license"))
            } }.also { require(it.map(CatalogFont::id).distinct().size == it.size) }
        }
        private fun address(value: String): String = value.also {
            require(value.startsWith(BASE) && URI(value).path.split('/').none { it == ".." })
        }
        private fun fetch(address: String, limit: Int): ByteArray {
            var next = address
            repeat(6) {
                require(URI(next).let { uri -> uri.scheme == "https" && uri.host != null && uri.userInfo == null })
                val connection = URL(next).openConnection() as HttpURLConnection
                connection.connectTimeout = 12000; connection.readTimeout = 20000; connection.instanceFollowRedirects = false
                try {
                    if (connection.responseCode in 300..399) next = URL(URL(next), requireNotNull(connection.getHeaderField("Location"))).toString()
                    else {
                        require(connection.responseCode == 200) { "暂时无法连接字体库，请稍后重试" }
                        return connection.inputStream.use { AppearanceAssets.readBounded(it, limit).also { bytes -> require(bytes.isNotEmpty()) } }
                    }
                } finally { connection.disconnect() }
            }
            error("字体下载跳转过多")
        }
    }
}
