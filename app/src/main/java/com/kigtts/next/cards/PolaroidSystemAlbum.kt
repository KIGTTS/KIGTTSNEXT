package com.kigtts.next.cards

import android.content.Context
import android.net.Uri
import android.util.Log
import com.kigtts.next.media.*
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Persistent export receipts prevent duplicate photos on launch, reorder, or repeated saves. */
internal class PolaroidSystemAlbum(private val context: Context, private val stickers: StickerRepository,
    private val publication: GalleryImagePublication = GalleryImages(context),
    private val render: suspend (PolaroidCard) -> File = { exportPolaroidMedia(stickers, it, maximumHeight = galleryPolaroidHeight(stickers, it)) },
    private val permission: suspend () -> Boolean = { requestGalleryWrite(context) }) {
    private val preferences = context.getSharedPreferences("polaroid-system-album", Context.MODE_PRIVATE)
    private val gate = Mutex()
    private var permissionDeclined = false

    suspend fun synchronize(): Int = withContext(Dispatchers.IO) { gate.withLock {
        val photos = stickers.cards.value.filter { it.imageId != null && it.frameStyleId != "identity.banner" }
        val pending = photos.filter { receipt(it.id)?.optString("fingerprint") != fingerprint(it) }
        if (pending.isEmpty() || permissionDeclined) return@withLock 0
        if (!permission()) { permissionDeclined = true; return@withLock 0 }
        var failed = 0
        for (card in pending) {
            currentCoroutineContext().ensureActive()
            try { publish(card, force = false) }
            catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { failed++; Log.w("KigSystemAlbum", "Could not save polaroid ${card.id}", failure) }
        }
        failed
    } }

    suspend fun save(cardId: String): Uri = gate.withLock {
        check(permission()) { "未允许保存到系统相册" }; permissionDeclined = false
        val card = requireNotNull(stickers.cards.value.firstOrNull { it.id == cardId }) { "这张拍立得已移除" }
        publish(card, force = true) ?: error("照片已更新，请重新保存")
    }

    private suspend fun publish(card: PolaroidCard, force: Boolean): Uri? = withContext(Dispatchers.IO) {
        val hash = fingerprint(card); val previous = receipt(card.id)
        val previousUri = previous?.optString("uri")?.takeIf(String::isNotBlank)?.let(Uri::parse)
        if (previous?.optString("fingerprint") == hash) {
            if (!force || previousUri != null && publication.exists(previousUri)) return@withContext previousUri
        }
        if (stickers.cards.value.firstOrNull { it.id == card.id } != card) return@withContext null
        val image = render(card)
        try {
            // An edit or removal during export must not publish a stale frame or recreate a deleted card.
            if (stickers.cards.value.firstOrNull { it.id == card.id } != card || fingerprint(card) != hash) return@withContext null
            val uri = publication.publish(image, "拍立得", stickers.cardCreatedAt(card.id))
            // Complete the receipt and replacement even if the UI owner is cancelled after MediaStore committed.
            withContext(NonCancellable) {
                if (!preferences.edit().putString(card.id, JSONObject().put("uri", uri.toString()).put("fingerprint", hash).toString()).commit()) {
                    publication.remove(uri); error("未能记录系统相册保存状态")
                }
                previousUri?.takeIf { it != uri }?.let { runCatching { publication.remove(it) } }
            }
            uri
        } finally {
            if (image.name.endsWith("MP.jpg", true) && image.canonicalFile.parentFile == File(stickers.root, "exports").canonicalFile) image.delete()
        }
    }
    private fun receipt(id: String): JSONObject? = runCatching { preferences.getString(id, null)?.let(::JSONObject) }.getOrNull()
    private fun fingerprint(card: PolaroidCard): String {
        val source = "hd-v1/" + PluginJson.encodeToString(card) + PluginJson.encodeToString(stickers.frameStyle(card))
        return MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

/** Add paper around the source image; final rendering has a bounded 12MP canvas. */
internal fun galleryPolaroidHeight(stickers: StickerRepository, card: PolaroidCard): Int {
    val source = stickers.images.value.firstOrNull { it.id == card.imageId }
    val geometry = polaroidPaperGeometry(card, stickers.frameStyle(card))
    val fraction = (geometry.photoBottom - geometry.photoTop) / geometry.paperHeight
    return ((source?.let { maxOf(it.width, it.height) } ?: 3000) / fraction).toInt().coerceIn(3000, 6144)
}

internal fun observePolaroidSystemAlbum(scope: CoroutineScope, cards: CardRepository, message: (String) -> Unit) {
    scope.launch {
        // Resource-library or album-order edits cannot change a rendered photograph.
        combine(cards.stickers.cards, cards.stickers.frameStyles) { _, _ -> Unit }.collect {
            if (cards.systemAlbum.synchronize() > 0) message("部分拍立得未能保存到系统相册，可在照片预览中重试")
        }
    }
}
