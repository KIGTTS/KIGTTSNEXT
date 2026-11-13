package com.kigtts.next.cards

import android.net.Uri
import com.kigtts.next.media.EmbeddedPhotoOriginal
import com.kigtts.next.media.CameraPhotoEncoding
import com.kigtts.plugin.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Import only the image the user opened; cancelled drafts never appear in the system album. */
internal object PhotoDocumentsDrafts {
    fun isDraft(context: android.content.Context, id: String) = context.getSharedPreferences("photo-documents", 0).getBoolean("draft:$id", false)
}

class PhotoDocuments internal constructor(private val cards: CardRepository, private val stickers: StickerRepository) {
    private val preferences = stickers.context.getSharedPreferences("photo-documents", 0)
    fun isDraft(id: String) = preferences.getBoolean("draft:$id", false)
    fun commit(id: String) { preferences.edit().remove("draft:$id").apply() }
    internal fun hold(id: String) { check(preferences.edit().putBoolean("draft:$id", true).commit()) }
    internal fun releasePublished(card: PolaroidCard) = synchronized(stickers) {
        val snapshot = stickers.snapshot.value
        if (snapshot.cards.none { it == card }) return@synchronized
        val ids = listOfNotNull(card.imageId, card.originalImageId, card.photoStyle?.lutImageId).distinct().filter { id ->
            snapshot.items.none { it.imageId == id } &&
                snapshot.cards.none { it.id != card.id && id in imageReferences(it) } &&
                stickers.identityDesigns.items.value.none { id in imageReferences(it) } &&
                stickers.frameStyles.value.none { it.overlayImageId == id } &&
                id !in stickers.designs.referencedImages() && snapshot.images.any { it.id == id }
        }
        stickers.apply(listOf(StickerEdit.RemoveCards(listOf(card.id))) +
            if (ids.isEmpty()) emptyList() else listOf(StickerEdit.RemoveImages(ids)))
        runCatching { stickers.designs.forgetFacts(card.id) }
        commit(card.id)
        cards.systemAlbum.forgetReceipt(card.id)
        cards.systemAlbum.encodings.remove(card.id)
        preferences.all.filter { (key, value) -> key.startsWith("source:") && value == card.id }.keys.forEach {
            preferences.edit().remove(it).apply()
        }
    }

    private fun imageReferences(card: PolaroidCard) = listOfNotNull(card.imageId, card.originalImageId, card.photoStyle?.lutImageId) +
        card.layers.mapNotNull { it.imageId }
    internal fun update(card: PolaroidCard): PolaroidCard {
        val draft = isDraft(card.id)
        val embedded = isEmbedded(card.id)
        if (draft && !embedded) commit(card.id)
        return try { cards.update(card) } catch (failure: Exception) {
            if (draft) preferences.edit().putBoolean("draft:${card.id}", true).apply()
            throw failure
        }
    }
    internal fun isEmbedded(id: String): Boolean = preferences.getBoolean("embedded:$id", false)
    internal suspend fun saveEmbedded(card: PolaroidCard): Uri = withContext(Dispatchers.IO) {
        val uri = cards.systemAlbum.save(card.id, embedOriginal = true)
        withContext(kotlinx.coroutines.NonCancellable) {
            runCatching { releasePublished(card); preferences.edit().remove("embedded:${card.id}").apply() }
                .onFailure { android.util.Log.w("KigPhotoDocuments", "Could not clean up photo draft", it) }
        }
        uri
    }
    internal suspend fun discardEmbedded(id: String) = withContext(Dispatchers.IO) {
        if (isEmbedded(id)) cards.cards.value.firstOrNull { it.id == id }?.let {
            releasePublished(it)
            preferences.edit().remove("embedded:$id").apply()
        }
    }
    internal suspend fun writeEmbeddedCopy(card: PolaroidCard, uri: Uri): Uri = withContext(Dispatchers.IO) {
        val source = requireNotNull((card.originalImageId ?: card.imageId)?.let(stickers::imageFile))
        val image = cards.systemAlbum.encodings.stage(card, CameraPhotoEncoding.JPEG, null)
        val packed = File.createTempFile("camera-embedded-", ".jpg", stickers.context.cacheDir)
        try {
            EmbeddedPhotoOriginal.write(image, source, EmbeddedCameraEdit.encode(card, stickers.designs.facts(card.id).capturedAt), packed)
            requireNotNull(stickers.context.contentResolver.openOutputStream(uri, "wt")).use { target ->
                packed.inputStream().use { it.copyTo(target, 64 * 1024) }
            }
            discardEmbedded(card.id)
            uri
        } finally { image.delete(); packed.delete() }
    }
    suspend fun open(uri: Uri): String = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "请选择可访问的图片" }
        cards.systemAlbum.cardFor(uri)?.takeIf { id -> cards.cards.value.any { it.id == id } }?.let { return@withContext it }
        val key = "source:$uri"
        preferences.getString(key, null)?.takeIf { id -> cards.cards.value.any { it.id == id } }?.let { return@withContext it }
        val container = File.createTempFile("photo-document-", ".tmp", stickers.context.cacheDir)
        val extracted = File(stickers.context.cacheDir, "photo-original-${UUID.randomUUID()}.jpg")
        try {
            val input = requireNotNull(stickers.context.contentResolver.openInputStream(uri)) { "无法读取所选照片" }
            val buffer = ByteArray(64 * 1024)
            input.use { source -> container.outputStream().use { target ->
                var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = source.read(buffer); if (read < 0) break
                    total += read; require(total <= 224L * 1024 * 1024) { "照片文件过大" }
                    target.write(buffer, 0, read)
                }
            } }
            // Retain an existing live clip as-is until portable live-edit metadata is supported.
            val metadata = if (com.kigtts.next.media.MotionPhoto.locate(container) != null) null
                else EmbeddedPhotoOriginal.extract(container, extracted)?.let { EmbeddedCameraEdit.decode(it.recipe) }
            val original = metadata?.let { stickers.importResource(extracted) }
            val image = original?.takeIf { !metadata.flattened } ?: stickers.importResource(container)
            val id = UUID.randomUUID().toString()
            check(preferences.edit().putBoolean("draft:$id", true).putString(key, id)
                .putBoolean("embedded:$id", metadata != null).commit())
            if (metadata != null) cards.systemAlbum.encodings.set(id,
                if (stickers.context.contentResolver.getType(uri) in setOf("image/heic", "image/heif")) CameraPhotoEncoding.HEIF else CameraPhotoEncoding.JPEG)
            val card = PolaroidCard(id = id, title = "", imageId = image.id, originalImageId = original?.id ?: image.id,
                frameStyleId = PhotoFrameSpec(image.width, image.height).id)
            cards.createCameraPhoto(metadata?.takeUnless { it.flattened }?.apply(card) ?: card,
                PolaroidPhotoFacts.read(if (metadata != null) extracted else container, false).let { facts ->
                    facts.copy(capturedAt = metadata?.takenAt ?: facts.capturedAt)
                })
            id
        } finally { container.delete(); extracted.delete() }
    }
}
