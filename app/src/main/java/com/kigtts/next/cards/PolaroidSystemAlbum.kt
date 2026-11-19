package com.kigtts.next.cards

import com.kigtts.next.i18n.uiCopy

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
    val encodings = CameraAlbumEncoding(stickers)
    private val gate = Mutex()
    private var permissionDeclined = false

    suspend fun synchronize(): Int = withContext(Dispatchers.IO) { gate.withLock {
        val photos = stickers.cards.value.filter { it.imageId != null && it.frameStyleId != "identity.banner" && !PhotoDocumentsDrafts.isDraft(context, it.id) }
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

    suspend fun save(cardId: String, embedOriginal: Boolean = false): Uri = gate.withLock {
        check(permission()) { "未允许保存到系统相册" }; permissionDeclined = false
        val card = requireNotNull(stickers.cards.value.firstOrNull { it.id == cardId }) { "这张照片已移除" }
        publish(card, force = true, embedOriginal = embedOriginal) ?: error("照片已更新，请重新保存")
    }

    private suspend fun publish(card: PolaroidCard, force: Boolean, embedOriginal: Boolean = false): Uri? = withContext(Dispatchers.IO) {
        val cardHash = fingerprint(card)
        val hash = cardHash + if (embedOriginal) "/embedded-v1" else ""
        val previous = receipt(card.id)
        val previousUri = previous?.optString("uri")?.takeIf(String::isNotBlank)?.let(Uri::parse)
        if (previous?.optString("fingerprint") == hash) {
            if (!force || previousUri != null && publication.exists(previousUri)) return@withContext previousUri
        }
        if (stickers.cards.value.firstOrNull { it.id == card.id } != card) return@withContext null
        val frame = PhotoFrameSpec.from(card.frameStyleId)
        val motion = CardMotionRepository(stickers).get(card.imageId) != null
        val untouched = frame?.style == "none" && frame.crop == null && card.photoStyle == null && card.layers.isEmpty() &&
            card.title.isEmpty() && card.description.isEmpty() && !motion && card.photoZoom == 1.0 && card.photoX == .5 && card.photoY == .5 &&
            (card.originalImageId == null || card.originalImageId == card.imageId)
        val original = if (untouched) requireNotNull(card.imageId?.let(stickers::imageFile)) else null
        val format = encodings.get(card.id)
        val staged = !motion && format != null && (format == CameraPhotoEncoding.HEIF || original == null)
        val image = if (staged) encodings.stage(card, requireNotNull(format), original) else original ?: render(card)
        var embedded: File? = null
        try {
            val source = (card.originalImageId ?: card.imageId)?.let(stickers::imageFile)
            val facts = stickers.designs.facts(card.id)
            val takenAt = facts.capturedAt
            if (embedOriginal && image != requireNotNull(source) { "原图不存在" }) {
                val target = File.createTempFile("camera-embedded-", if (motion) "MP.jpg" else ".tmp", context.cacheDir)
                embedded = target
                EmbeddedPhotoOriginal.write(image, requireNotNull(source), EmbeddedCameraEdit.encode(card, facts), target)
            }
            // An edit or removal during export must not publish a stale frame or recreate a deleted card.
            if (stickers.cards.value.firstOrNull { it.id == card.id } != card || fingerprint(card) != cardHash) return@withContext null
            val uri = publication.publish(embedded ?: image, if (frame != null) "相机" else "拍立得",
                takenAt ?: stickers.cardCreatedAt(card.id))
            // Complete the receipt and replacement even if the UI owner is cancelled after MediaStore committed.
            withContext(NonCancellable) {
                if (!preferences.edit().putString(card.id, JSONObject().put("uri", uri.toString()).put("fingerprint", hash)
                        .put("heif", image.extension.equals("heic", true) || image.extension.equals("heif", true)).toString()).commit()) {
                    publication.remove(uri); error("未能记录系统相册保存状态")
                }
                previousUri?.takeIf { it != uri }?.let { runCatching { publication.remove(it) } }
            }
            uri
        } finally {
            embedded?.delete()
            if (staged && image != original ||
                image.name.endsWith("MP.jpg", true) && image.canonicalFile.parentFile == File(stickers.root, "exports").canonicalFile) image.delete()
        }
    }
    fun cardFor(uri: Uri): String? = preferences.all.keys.firstOrNull { receipt(it)?.optString("uri") == uri.toString() }
    internal fun forgetReceipt(id: String) { preferences.edit().remove(id).apply() }
    fun uriFor(id: String): Uri? = receipt(id)?.optString("uri")?.takeIf(String::isNotBlank)?.let(Uri::parse)
    fun isHeif(id: String): Boolean = receipt(id)?.optBoolean("heif") == true
    private fun receipt(id: String): JSONObject? = runCatching { preferences.getString(id, null)?.let(::JSONObject) }.getOrNull()
    private fun fingerprint(card: PolaroidCard): String {
        val source = "hd-v1/" + PluginJson.encodeToString(card) + PluginJson.encodeToString(stickers.frameStyle(card)) +
            (encodings.get(card.id)?.let { "/${it.name}" } ?: "")
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
            if (cards.systemAlbum.synchronize() > 0) message(cards.stickers.context.uiCopy("album.sync_failed"))
        }
    }
}
