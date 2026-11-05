package com.kigtts.next.sharing

import com.kigtts.plugin.api.PLUGIN_API_VERSION
import com.kigtts.plugin.runtime.PackagePreview
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.UUID

internal const val CLOUD_SHARING_ID = "cloud.sharing"
private const val PUBLISHER = "b5927c76c21539ef99352707c215b3ce48f3fed937a6f995d39a3c677a052ab5"
internal class CloudSharingDownload(private val directory: File, private val inspect: (File) -> PackagePreview) {
    suspend fun download(progress: (Float) -> Unit = {}): Pair<File, PackagePreview> {
        progress(0f)
        val info = JSONObject(read("https://app.kigtts.lhtstudio.com/plugins/cloud-sharing.json", 16 * 1024).toString(Charsets.UTF_8))
        val version = info.getString("version")
        val code = info.getInt("versionCode")
        val size = info.getLong("bytes")
        val hash = info.getString("sha256")
        require(version.matches(Regex("[0-9A-Za-z.+-]{1,64}")) && code > 0 &&
            size in 1..8L * 1024 * 1024 && hash.matches(Regex("[a-f0-9]{64}"))) { "云共享下载信息无效" }
        val file = File(directory.apply { mkdirs() }, ".cloud-sharing-${UUID.randomUUID()}.kigplugin")
        try {
            val bytes = read("https://app.kigtts.lhtstudio.com/plugins/cloud.sharing-$version.kigplugin", size, progress)
            require(bytes.size.toLong() == size && MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } == hash) { "云共享下载校验失败" }
            file.writeBytes(bytes)
            val preview = inspect(file)
            require(preview.publisherSha256 == PUBLISHER && preview.manifest.let {
                it.id == CLOUD_SHARING_ID && it.version == version && it.versionCode == code &&
                    it.apiVersion <= PLUGIN_API_VERSION && it.minSdk <= android.os.Build.VERSION.SDK_INT &&
                    it.capabilities.toSet() == setOf("ui", "network")
            }) { "云共享签名或版本不匹配，请更新软件后重试" }
            return file to preview
        } catch (error: Throwable) { file.delete(); throw error }
    }
    private suspend fun read(url: String, limit: Long, progress: ((Float) -> Unit)? = null): ByteArray {
        val uri = URI(url)
        require(uri.scheme == "https" && uri.host == "app.kigtts.lhtstudio.com" && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
        val connection = uri.toURL().openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000; connection.readTimeout = 20_000
            connection.setRequestProperty("Accept-Encoding", "identity")
            check(connection.responseCode == 200) { "云共享暂时无法下载，请稍后重试" }
            require(connection.contentLengthLong <= limit)
            return connection.inputStream.use { input -> java.io.ByteArrayOutputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var previousPercent = -1
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer); if (count < 0) break
                    require(output.size() + count <= limit) { "下载文件过大" }
                    output.write(buffer, 0, count)
                    val percent = (output.size() * 100L / limit).toInt()
                    if (percent != previousPercent) {
                        previousPercent = percent
                        progress?.invoke(output.size().toFloat() / limit)
                    }
                }
                output.toByteArray()
            } }
        } finally { connection.disconnect() }
    }
}
