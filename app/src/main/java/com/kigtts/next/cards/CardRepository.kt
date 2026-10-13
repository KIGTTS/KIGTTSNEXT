package com.kigtts.next.cards

import android.content.Context
import android.net.Uri
import com.kigtts.next.ui.cards.CardStore
import com.kigtts.plugin.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** The UI and plugin broker edit exactly the same design catalog. */
class CardRepository(private val context:Context,val stickers:StickerRepository) {
    val cards=stickers.cards
    fun create(card:PolaroidCard):PolaroidCard=synchronized(stickers) {
        stickerRequire(cards.value.none {it.id==card.id},message="拍立得已存在")
        stickers.apply(listOf(StickerEdit.PutCard(card)));card
    }
    fun update(card:PolaroidCard):PolaroidCard=synchronized(stickers) {
        stickerRequire(cards.value.any {it.id==card.id},StickerError.NOT_FOUND,"拍立得不存在")
        stickers.apply(listOf(StickerEdit.PutCard(card)));card
    }
    fun remove(id:String) {stickers.apply(listOf(StickerEdit.RemoveCards(listOf(id))))}
    /** IDs are deterministic so an interrupted migration never duplicates old cards. */
    suspend fun migrateLegacy()=withContext(Dispatchers.IO) {
        synchronized(stickers) {
            if(stickers.legacyMigrated)return@synchronized
            val legacy=CardStore(context).load()
            val cards=legacy.mapIndexedNotNull {index,old->
                val id=UUID.nameUUIDFromBytes("legacy-card/$index/${old.title}/${old.description}".toByteArray(Charsets.UTF_8)).toString()
                if(this@CardRepository.cards.value.any {it.id==id})null else {
                    val image=old.imageUri?.let {uri->runCatching {stickers.importImage(Uri.parse(uri)).id}.getOrNull()}
                    PolaroidCard(id,old.title.ifBlank {"拍立得"}.take(128),old.description.take(8192),image,frameStyleId="classic")
                }
            }
            cards.chunked(100).forEach {batch->stickers.apply(batch.map {StickerEdit.PutCard(it)})}
            stickers.markMigrated()
        }
    }
}
