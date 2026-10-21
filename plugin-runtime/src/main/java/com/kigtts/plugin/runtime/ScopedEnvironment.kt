package com.kigtts.plugin.runtime

import android.content.Context
import android.content.ContextWrapper
import com.kigtts.plugin.android.AndroidPluginEnvironment
import com.kigtts.plugin.api.PluginStorage
import com.kigtts.plugin.api.SpeechBroker
import com.kigtts.plugin.api.RecognitionBroker
import com.kigtts.plugin.api.QuickTextBroker
import com.kigtts.plugin.api.requirePermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import kotlin.coroutines.coroutineContext

internal class ScopedEnvironment(base: Context, override val storage: PluginStorage,
    override val assets: File, private val networkAllowed: Boolean,
    override val speechBroker: SpeechBroker? = null,
    override val recognitionBroker: RecognitionBroker? = null,override val quickTextBroker:QuickTextBroker?=null,
    override val appBroker: com.kigtts.plugin.api.AppBroker? = null,
    override val stickerBroker: com.kigtts.plugin.api.StickerBroker? = null) : AndroidPluginEnvironment {
    override val locale: String get() = context.resources.configuration.locales[0].toLanguageTag()
    override val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = storage.directory("files")
        override fun getCacheDir(): File = storage.directory("cache")
        override fun getExternalFilesDir(type: String?): File = storage.directory("files" + (type?.let { "/$it" } ?: ""))
        override fun getExternalCacheDir(): File = storage.directory("cache")
    }
    override suspend fun download(url: String, relativePath: String, sha256: String?): File = withContext(Dispatchers.IO) {
        require(networkAllowed) { "插件没有声明网络资源能力" }
        requirePermission("network")
        val destination = storage.file(relativePath)
        require(sha256 == null || sha256.matches(Regex("[a-fA-F0-9]{64}")))
        val temporary = File(storage.temporary, "download-${UUID.randomUUID()}.part")
        var current = URI(url)
        var connection: HttpURLConnection? = null
        try {
            for (redirect in 0..5) {
                require(current.scheme == "https" && !current.host.isNullOrBlank() && current.userInfo == null) { "资源地址必须使用 HTTPS" }
                coroutineContext.ensureActive()
                val active = current.toURL().openConnection() as HttpURLConnection
                connection = active
                active.instanceFollowRedirects = false
                active.connectTimeout = 15_000; active.readTimeout = 20_000
                val response = active.responseCode
                if (response in listOf(301, 302, 303, 307, 308)) {
                    require(redirect < 5) { "重定向次数过多" }
                    current = current.resolve(active.getHeaderField("Location") ?: error("无效重定向"))
                    active.disconnect(); continue
                }
                require(response in 200..299) { "下载失败（$response）" }
                require(active.contentLengthLong <= MAX_RESOURCE_BYTES) { "资源过大" }
                active.inputStream.use { input -> temporary.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var size = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer); if (count < 0) break
                        size += count; require(size <= MAX_RESOURCE_BYTES) { "资源过大" }
                        output.write(buffer, 0, count)
                    }
                } }
                if (sha256 != null) require(temporary.sha256().equals(sha256, true)) { "资源 SHA-256 不匹配" }
                destination.parentFile!!.mkdirs()
                java.nio.file.Files.move(temporary.toPath(), destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                return@withContext destination
            }
            error("下载失败")
        } finally { connection?.disconnect(); temporary.delete() }
    }
}

internal const val MAX_RESOURCE_BYTES = 2L * 1024 * 1024 * 1024
