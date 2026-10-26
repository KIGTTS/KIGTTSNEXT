package com.kigtts.next.identity

import android.graphics.*
import com.kigtts.next.cards.StickerRepository
import com.kigtts.next.media.MediaBitmapDecoder
import com.kigtts.next.media.thumbnail.ThumbnailMemoryCache
import com.kigtts.plugin.api.PolaroidCard
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest
import kotlin.math.ceil

/** Lossless display-sized bakes. Foreground identity text, avatars and stickers stay vector/native. */
internal class IdentityBackgroundCache(private val repository: StickerRepository,
    private val decode: (File, Int) -> Bitmap? = MediaBitmapDecoder::decode) {
    private val directory = File(repository.context.cacheDir, "identity-backgrounds-v2")
    private val memory = ThumbnailMemoryCache(24 * 1024 * 1024)
    private val sources = ThumbnailMemoryCache(48 * 1024 * 1024)
    private val mutex = Mutex()

    internal fun height(displayHeight: Int) = (ceil(displayHeight.coerceAtLeast(1) / 128.0).toInt() * 128).coerceIn(256, 1536)
    internal fun key(card: PolaroidCard, identity: IdentityCard): String {
        val image = card.imageId?.let(repository::imageFile)
        val border = repository.identityFrames.items.value.firstOrNull { it.id == identity.cardFrameId }?.imageId?.let(repository::imageFile)
        val input = listOf("v2", image?.absolutePath, image?.lastModified(), image?.length(), card.frameColor,
            card.photoX, card.photoY, card.photoZoom, card.photoBackdrop, identity.pattern, identity.gradient,
            identity.gradientColor.takeIf { identity.gradient }, identity.cardFrameId, border?.absolutePath, border?.lastModified(), border?.length()).joinToString("|")
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        val hex = "0123456789abcdef"
        return CharArray(bytes.size * 2) { i -> hex[if (i % 2 == 0) (bytes[i / 2].toInt() ushr 4) and 15 else bytes[i / 2].toInt() and 15] }.concatToString()
    }
    fun peek(card: PolaroidCard, identity: IdentityCard, displayHeight: Int): Bitmap? = memory["${key(card, identity)}-${height(displayHeight)}"]
    fun preview(card: PolaroidCard, identity: IdentityCard, displayHeight: Int): Bitmap? = peek(card, identity, displayHeight)
        ?: memory.snapshot().entries.filter { it.key.startsWith(key(card, identity) + "-") }.maxByOrNull { it.value.height }?.value

    suspend fun load(card: PolaroidCard, identity: IdentityCard, displayHeight: Int): Bitmap = withContext(Dispatchers.IO) {
        mutex.withLock {
            val height = height(displayHeight); val key = "${key(card, identity)}-$height"
            memory[key]?.let { return@withLock it }
            directory.mkdirs()
            val file = File(directory, "$key.png")
            val saved = if (file.isFile) BitmapFactory.decodeFile(file.path) else null
            if (saved != null && saved.height == height && saved.width == (height * 16f / 9f).toInt()) {
                memory.put(key, saved); file.setLastModified(System.currentTimeMillis()); return@withLock saved
            }
            saved?.recycle()
            val painter = prepareLocked(card, identity)
            val pixels = Bitmap.createBitmap((height * 16f / 9f).toInt(), height, Bitmap.Config.ARGB_8888)
            painter.draw(Canvas(pixels), pixels.width.toFloat(), pixels.height.toFloat(), card)
            currentCoroutineContext().ensureActive()
            // Atomic replacement; failed cache writes do not stop the preview.
            val stage = File.createTempFile("bake-", ".tmp", directory)
            try {
                runCatching { stage.outputStream().use { check(pixels.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    java.nio.file.Files.move(stage.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
            } finally { stage.delete() }
            memory.put(key, pixels)
            var bytes = 0L
            directory.listFiles()?.filter { it.extension == "png" }?.sortedByDescending { it.lastModified() }?.forEach {
                bytes += it.length(); if (bytes > 96L * 1024 * 1024 && it != file) it.delete()
            }
            pixels
        }
    }

    suspend fun prepare(card: PolaroidCard, identity: IdentityCard): IdentityBackgroundPainter = withContext(Dispatchers.Default) {
        mutex.withLock { prepareLocked(card, identity) }
    }
    private fun prepareLocked(card: PolaroidCard, identity: IdentityCard): IdentityBackgroundPainter {
        val file = card.imageId?.let(repository::imageFile)
        val stamp = "${file?.absolutePath}|${file?.lastModified()}|${file?.length()}"
        fun cached(suffix: String, make: () -> Bitmap?): Bitmap? = sources[stamp + suffix] ?: make()?.also { sources.put(stamp + suffix, it) }
        val photo = file?.let { cached("photo") { decode(it, 3072) } }
        fun blurred(source: Bitmap, maximum: Int, sigma: Float): Bitmap {
            val factor = minOf(1f, maximum.toFloat() / maxOf(source.width, source.height))
            val scaled = Bitmap.createScaledBitmap(source, (source.width * factor).toInt().coerceAtLeast(1), (source.height * factor).toInt().coerceAtLeast(1), true)
            return try { com.kigtts.next.theme.gaussianBlur(scaled, sigma) } finally { if (scaled !== source) scaled.recycle() }
        }
        val backdrop = photo?.let { cached("backdrop") { blurred(it, 256, 10f) } }
        val soft = photo?.takeIf { identity.gradient }?.let { cached("soft") {
            blurred(it, 512, 6f)
        } }
        return IdentityBackgroundPainter.create(card, identity, repository, photo, backdrop, soft)
    }
}
