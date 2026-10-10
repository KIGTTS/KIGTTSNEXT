package com.kigtts.next.media.thumbnail

import android.graphics.Bitmap
import com.kigtts.next.media.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

/** Repository-scoped, memory-only previews. Reading a card never opens a WebView or plays media. */
class MediaThumbnails(private val repository: MediaRepository) {
    private val sources = ThumbnailMemoryCache(4 * 1024 * 1024)
    private val rendered = ThumbnailMemoryCache(6 * 1024 * 1024)
    private val webPreviews = ThumbnailMemoryCache(4 * 1024 * 1024)
    private val webVersions = ConcurrentHashMap<String, Long>()
    private val knownSources = ConcurrentHashMap<String, String>()
    private val lastRender = object : android.util.LruCache<String, String>(128) {}
    private val failed = object : android.util.LruCache<String, Boolean>(128) {}
    // Serialize expensive work, deduplicate card/drag/dialog requests and bound transient memory.
    private val sourceLock = Mutex(); private val renderLock = Mutex()
    private val changes = MutableStateFlow(0L)
    val revision = changes.asStateFlow()

    private fun sourceKey(entry: MediaEntry): String {
        if (entry.kind in setOf(MediaKind.HTML, MediaKind.WEB)) return webKey(entry)
        val file = repository.file(entry)
        return "${entry.id}/${entry.kind}/${entry.pluginId}/${entry.primary}/${file?.lastModified()}/${file?.length()}/${webVersions[entry.id] ?: 0}"
    }
    private fun webKey(entry: MediaEntry) = "${entry.id}/${entry.kind}/${entry.pluginId}/${entry.primary}/${entry.created}/${webVersions[entry.id] ?: 0}"
    private fun framingKey(entry: MediaEntry) = "${entry.thumbnailMode}/${entry.focusX}/${entry.focusY}"
    private fun renderKey(source: String, entry: MediaEntry, width: Int, height: Int) = "$source/${framingKey(entry)}/$width/$height"

    /** Memory lookups only: no filesystem stat or plugin path resolution on the composition thread. */
    fun cached(entry: MediaEntry, width: Int, height: Int): Bitmap? = knownSources[entry.id]?.let { rendered.get(renderKey(it, entry, width, height)) }
    fun latest(entry: MediaEntry): Bitmap? = lastRender.get("${entry.id}/${framingKey(entry)}")?.let(rendered::get)
    fun refresh() { changes.update { it + 1 } }

    private data class Source(val key: String, val bitmap: Bitmap)
    suspend fun source(entry: MediaEntry): Bitmap? = load(entry)?.bitmap
    private suspend fun load(entry: MediaEntry): Source? = withContext(Dispatchers.IO) {
        if (entry.kind == MediaKind.AUDIO) return@withContext null
        sourceLock.withLock {
            val key = sourceKey(entry)
            knownSources[entry.id] = key
            if (entry.kind in setOf(MediaKind.HTML, MediaKind.WEB)) return@withLock webPreviews.get(key)?.let { Source(key, it) }
            sources.get(key)?.let { return@withLock Source(key, it) }
            if (failed.get(key) == true) return@withLock null
            val file = repository.file(entry) ?: return@withLock null
            val decoded = try {
                if (entry.kind == MediaKind.VIDEO) VideoThumbnailDecoder.decode(file) else MediaBitmapDecoder.decode(file, 384)
            } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { null }
            currentCoroutineContext().ensureActive()
            if (decoded != null) sources.put(key, decoded) else failed.put(key, true)
            decoded?.let { Source(key, it) }
        }
    }

    suspend fun thumbnail(entry: MediaEntry, width: Int, height: Int): Bitmap? {
        require(width in 1..384 && height in 1..256)
        val source = load(entry) ?: return null
        return withContext(Dispatchers.Default) {
            renderLock.withLock {
                val key = renderKey(source.key, entry, width, height)
                rendered.get(key)?.let { lastRender.put("${entry.id}/${framingKey(entry)}", key); return@withLock it }
                val context = currentCoroutineContext()
                val bitmap = ThumbnailRenderer.render(source.bitmap, width, height, entry, context::ensureActive)
                context.ensureActive()
                rendered.put(key, bitmap)
                lastRender.put("${entry.id}/${framingKey(entry)}", key)
                bitmap
            }
        }
    }

    /** A screenshot of an already displayed page; no hidden page or network request. */
    fun rememberWeb(entry: MediaEntry, bitmap: Bitmap) {
        require(bitmap.width <= 512 && bitmap.height <= 512)
        webVersions.compute(entry.id) { _, value -> (value ?: 0) + 1 }
        webPreviews.snapshot().keys.filter { it.startsWith("${entry.id}/") }.forEach(webPreviews::remove)
        val key = webKey(entry); knownSources[entry.id] = key; webPreviews.put(key, bitmap)
        changes.update { it + 1 }
    }

    fun remove(id: String) {
        sources.snapshot().keys.filter { it.startsWith("$id/") }.forEach(sources::remove)
        rendered.snapshot().keys.filter { it.startsWith("$id/") }.forEach(rendered::remove)
        webPreviews.snapshot().keys.filter { it.startsWith("$id/") }.forEach(webPreviews::remove)
        failed.snapshot().keys.filter { it.startsWith("$id/") }.forEach(failed::remove)
        webVersions.remove(id); knownSources.remove(id)
        lastRender.snapshot().keys.filter { it.startsWith("$id/") }.forEach(lastRender::remove)
        changes.update { it + 1 }
    }

    companion object {
        /** Size buckets prevent press/layout animation from regenerating a preview on every pixel. */
        fun size(width: Int, height: Int): Pair<Int, Int> {
            val factor = min(1.0, min(384.0 / width.coerceAtLeast(1), 256.0 / height.coerceAtLeast(1)))
            val w = (width * factor).roundToInt().coerceAtLeast(1)
            val h = (height * factor).roundToInt().coerceAtLeast(1)
            return (((w + 15) / 32) * 32).coerceIn(32, 384) to (((h + 7) / 16) * 16).coerceIn(16, 256)
        }
    }
}
