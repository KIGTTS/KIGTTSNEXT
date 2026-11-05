package com.kigtts.next.cards

import android.net.Uri
import com.kigtts.plugin.api.PolaroidCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** Import only the image the user opened; cancelled drafts never appear in the system album. */
internal object PhotoDocumentsDrafts {
    fun isDraft(context: android.content.Context, id: String) = context.getSharedPreferences("photo-documents", 0).getBoolean("draft:$id", false)
}

class PhotoDocuments internal constructor(private val cards: CardRepository, private val stickers: StickerRepository) {
    private val preferences = stickers.context.getSharedPreferences("photo-documents", 0)
    fun isDraft(id: String) = preferences.getBoolean("draft:$id", false)
    fun commit(id: String) { preferences.edit().remove("draft:$id").apply() }
    internal fun update(card: PolaroidCard): PolaroidCard {
        val draft = isDraft(card.id)
        if (draft) commit(card.id)
        return try { cards.update(card) } catch (failure: Exception) {
            if (draft) preferences.edit().putBoolean("draft:${card.id}", true).apply()
            throw failure
        }
    }
    suspend fun open(uri: Uri): String = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "请选择可访问的图片" }
        cards.systemAlbum.cardFor(uri)?.takeIf { id -> cards.cards.value.any { it.id == id } }?.let { return@withContext it }
        val key = "source:$uri"
        preferences.getString(key, null)?.takeIf { id -> cards.cards.value.any { it.id == id } }?.let { return@withContext it }
        val image = stickers.importImage(uri)
        val id = UUID.randomUUID().toString()
        check(preferences.edit().putBoolean("draft:$id", true).putString(key, id).commit())
        synchronized(stickers) { cards.create(PolaroidCard(id = id, title = "", imageId = image.id,
            originalImageId = image.id, frameStyleId = PhotoFrameSpec(image.width, image.height).id)) }
        id
    }
}
