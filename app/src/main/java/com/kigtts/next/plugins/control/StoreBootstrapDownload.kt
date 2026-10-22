package com.kigtts.next.plugins.control

import android.os.Build
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PackagePreview
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.UUID

internal const val STORE_ID = "tools.package-store"
internal const val STORE_PUBLISHER = "b5927c76c21539ef99352707c215b3ce48f3fed937a6f995d39a3c677a052ab5"
internal data class StoreDownload(val file: File, val preview: PackagePreview)
internal data class StoreRelease(val version: String, val code: Int, val api: Int, val minSdk: Int,
    val abis: Set<String>, val bytes: Long, val hash: String, val mirrors: List<String>) {
    fun matches(preview: PackagePreview) = preview.publisherSha256 == STORE_PUBLISHER && preview.manifest.let {
        it.id == STORE_ID && it.version == version && it.versionCode == code && it.apiVersion == api &&
            it.minSdk == minSdk && it.abis.toSet() == abis
    }
}

/** Only bootstraps the official store. The proposed third-party source is never fetched here. */
internal class StoreBootstrapDownload(private val directory: File, private val inspect: (File) -> PackagePreview) {
    suspend fun download(): StoreDownload {
        val catalog = java.io.ByteArrayOutputStream()
        read(OfficialPluginSource.URL, 4L * 1024 * 1024) { buffer, count -> catalog.write(buffer, 0, count) }
        val release = select(catalog.toByteArray(), Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS.toSet())
        var failure: Exception? = null
        for (mirror in release.mirrors) {
            currentCoroutineContext().ensureActive()
            val file = File(directory, ".store-bootstrap-${UUID.randomUUID()}.kigplugin")
            try {
                directory.mkdirs()
                val digest = MessageDigest.getInstance("SHA-256")
                val count = file.outputStream().use { output -> read(mirror, release.bytes) { buffer, size ->
                    output.write(buffer, 0, size); digest.update(buffer, 0, size)
                } }
                require(count == release.bytes && digest.digest().joinToString("") { "%02x".format(it) } == release.hash) { "插件商店下载校验失败" }
                val preview = inspect(file)
                require(release.matches(preview)) { "插件商店签名或版本与官方目录不一致" }
                currentCoroutineContext().ensureActive()
                return StoreDownload(file, preview)
            } catch (cancel: kotlinx.coroutines.CancellationException) { file.delete(); throw cancel }
            catch (error: Exception) { file.delete(); currentCoroutineContext().ensureActive(); failure = error }
        }
        throw failure ?: IllegalStateException("插件商店暂无可用下载")
    }

    private suspend fun read(url: String, limit: Long, consume: (ByteArray, Int) -> Unit): Long {
        var target = https(url)
        repeat(6) { redirect ->
            currentCoroutineContext().ensureActive()
            val connection = target.toURL().openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000; connection.readTimeout = 20_000
                connection.useCaches = false
                connection.setRequestProperty("Accept-Encoding", "identity")
                when (connection.responseCode) {
                    in listOf(301, 302, 303, 307, 308) -> {
                        require(redirect < 5) { "下载地址跳转过多" }
                        target = https(target.resolve(requireNotNull(connection.getHeaderField("Location"))).toString())
                    }
                    200 -> {
                        require(connection.contentLengthLong <= limit) { "下载文件过大" }
                        var total = 0L
                        connection.inputStream.use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer); if (count < 0) break
                                total += count; require(total <= limit) { "下载文件过大" }; consume(buffer, count)
                            }
                        }
                        return total
                    }
                    else -> error("插件商店下载失败（HTTP ${connection.responseCode}）")
                }
            } finally { connection.disconnect() }
        }
        error("下载地址跳转过多")
    }

    companion object {
        internal fun https(value: String): URI = URI(value).also {
            require(value.length in 1..4096 && it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null && it.fragment == null) { "下载地址必须使用 HTTPS" }
        }
        internal fun select(bytes: ByteArray, sdk: Int, abis: Set<String>): StoreRelease {
            require(bytes.size <= 4 * 1024 * 1024) { "官方目录过大" }
            val catalog = PluginJson.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
            require(catalog.getValue("schemaVersion").jsonPrimitive.int == 1 && catalog.getValue("id").jsonPrimitive.content == OfficialPluginSource.ID)
            val item = catalog.getValue("packages").jsonArray.single { it.jsonObject["id"]?.jsonPrimitive?.content == STORE_ID }.jsonObject
            val releases = item.getValue("releases").jsonArray.map { value ->
                val release = value.jsonObject
                fun string(key: String) = release.getValue(key).jsonPrimitive.content
                require(string("format") == "NATIVE" && string("expectedPublisher") == STORE_PUBLISHER) { "官方商店发布者不一致" }
                val mirrors = release.getValue("mirrors").jsonArray.map { it.jsonObject.getValue("url").jsonPrimitive.content.also(::https) }
                require(mirrors.size in 1..8 && release["dependencies"]?.jsonObject.orEmpty().isEmpty()) { "商店引导包格式不支持" }
                StoreRelease(string("version"), release.getValue("versionCode").jsonPrimitive.int,
                    release.getValue("apiVersion").jsonPrimitive.int, release.getValue("minSdk").jsonPrimitive.int,
                    release["abis"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }.toSet(),
                    release.getValue("bytes").jsonPrimitive.long, string("sha256"), mirrors).also {
                    require(it.code > 0 && it.version.length in 1..64 && it.bytes in 1..256L * 1024 * 1024 && Regex("[a-f0-9]{64}").matches(it.hash))
                }
            }
            require(releases.size in 1..64 && releases.map { it.code }.distinct().size == releases.size)
            return releases.filter { it.api in MIN_PLUGIN_API_VERSION..PLUGIN_API_VERSION && it.minSdk <= sdk && (it.abis.isEmpty() || it.abis.any(abis::contains)) }
                .maxByOrNull { it.code } ?: error("暂无适合当前软件的插件商店，请先更新 KIGTTS")
        }
    }
}
