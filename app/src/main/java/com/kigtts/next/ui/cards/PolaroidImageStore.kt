package com.kigtts.next.ui.cards

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.kigtts.next.media.MediaBitmapDecoder
import com.kigtts.next.media.thumbnail.ThumbnailMemoryCache
import kotlinx.coroutines.*
import java.io.File

/** One bounded process cache; eviction does not recycle pixels already held by a visible card. */
internal class PolaroidImageStore(maxBytes: Int = 16 * 1024 * 1024,
    private val decode: (File, Int) -> Bitmap? = { file, maximum -> MediaBitmapDecoder.decode(file, maximum) }) {
    private val memory = ThumbnailMemoryCache(maxBytes)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val loading = HashMap<String, Deferred<Bitmap?>>()
    private var closed = false
    val memoryBytes: Int get() = memory.size()
    private fun key(file: File, maximum: Int) = file.absolutePath + "|" + file.lastModified() + "|" + file.length() + "|" + maximum
    fun peek(file: File?, maximum: Int): Bitmap? = file?.let { memory[key(it, maximum)] }
    suspend fun load(file: File, maximum: Int): Bitmap? {
        val key = key(file, maximum)
        val task = synchronized(lock) {
            if (closed) return null
            memory[key]?.let { return it }
            loading[key] ?: scope.async(start = CoroutineStart.LAZY) {
                val decoded = runCatching { decode(file, maximum) }.getOrNull()
                // Bitmap decoding can finish after cancellation; it must not refill a closed cache.
                if (decoded != null) synchronized(lock) { if (!closed) memory.put(key, decoded) }
                decoded
            }.also { request ->
                loading[key] = request
                request.invokeOnCompletion { synchronized(lock) { if (loading[key] === request) loading.remove(key) } }
                request.start()
            }
        }
        // Cancelling a hidden consumer does not cancel another visible consumer's shared decode.
        return task.await()
    }
    fun close() {
        synchronized(lock) { closed = true; memory.evictAll() }
        scope.cancel()
    }
}

private val cardImages = PolaroidImageStore()

@Composable
internal fun rememberCardImage(file: File?, maximum: Int = 384): ImageBitmap? {
    val stamp = file?.lastModified(); val bytes = file?.length()
    val initial = remember(file?.path, stamp, bytes, maximum) { cardImages.peek(file, maximum)?.asImageBitmap() }
    val image by produceState(initial, file?.path, stamp, bytes, maximum) {
        value = if (file == null) null else cardImages.load(file, maximum)?.asImageBitmap()
    }
    return image
}
