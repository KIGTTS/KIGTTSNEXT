package com.kigtts.tools.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection

interface StoreTransport {
    suspend fun fetch(url: String, destination: File, maximumBytes: Long, progress: (Long) -> Unit = {})
    fun cancel() {}
}
class HttpsStoreTransport : StoreTransport {
    private val active = AtomicReference<HttpsURLConnection?>()
    override fun cancel() { active.getAndSet(null)?.disconnect() }
    override suspend fun fetch(url: String, destination: File, maximumBytes: Long, progress: (Long) -> Unit) = withContext(Dispatchers.IO) {
        val connection = URL(CatalogCodec.https(url)).openConnection() as HttpsURLConnection
        check(active.compareAndSet(null, connection)) { "已有下载正在进行" }
        try {
            connection.connectTimeout = 20_000; connection.readTimeout = 30_000; connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json, application/octet-stream, application/zip")
            check(connection.responseCode == 200) { "下载地址暂时不可用（${connection.responseCode}）" }
            require(connection.contentLengthLong <= maximumBytes) { "下载文件超过大小限制" }
            destination.parentFile?.mkdirs()
            connection.inputStream.use { input -> destination.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024); var bytes = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer); if (count < 0) break
                    bytes += count; require(bytes <= maximumBytes) { "下载文件超过大小限制" }
                    output.write(buffer, 0, count); progress(bytes)
                }
            } }
        } finally { active.compareAndSet(connection, null); connection.disconnect() }
    }
}
