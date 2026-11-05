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
    val documents by lazy { PhotoDocuments(this, stickers) }
    internal val systemAlbum by lazy { PolaroidSystemAlbum(context, stickers) }
    val cards=stickers.cards
    val albums=stickers.albums
    val designs=stickers.designs
    fun createPhoto(card:PolaroidCard, facts:FrozenPhotoFacts, albumId:String=DEFAULT_ALBUM):PolaroidCard=synchronized(stickers) {
        stickerRequire(cards.value.none { it.id==card.id },message="拍立得已存在")
        designs.freeze(card.id,facts)
        create(designs.apply(card),albumId)
    }
    fun cardsInAlbum(albumId:String):List<PolaroidCard> = synchronized(stickers) {
        val value=stickers.snapshot.value;val album=value.albums.firstOrNull {it.id==albumId} ?: return@synchronized emptyList()
        val indexed=value.cards.associateBy {it.id};album.cardIds.mapNotNull {indexed[it]}
    }
    fun create(card:PolaroidCard):PolaroidCard=create(card,DEFAULT_ALBUM)
    fun create(card:PolaroidCard,albumId:String):PolaroidCard=synchronized(stickers) {
        stickerRequire(cards.value.none {it.id==card.id},message="拍立得已存在")
        val edits=listOf(StickerEdit.PutCard(card))+if(albumId==DEFAULT_ALBUM)emptyList() else listOf(StickerEdit.MoveAlbumCards(albumId,listOf(card.id)))
        stickers.apply(edits);card
    }
    fun update(card:PolaroidCard):PolaroidCard=synchronized(stickers) {
        stickerRequire(cards.value.any {it.id==card.id},StickerError.NOT_FOUND,"拍立得不存在")
        stickers.apply(listOf(StickerEdit.PutCard(card)));card
    }
    fun remove(id:String) {stickers.apply(listOf(StickerEdit.RemoveCards(listOf(id))))}
    fun removeCards(ids:List<String>) {stickers.apply(listOf(StickerEdit.RemoveCards(ids)))}
    fun createdAt(id:String):Long=stickers.cardCreatedAt(id)
    fun isReadOnly(id:String):Boolean=stickers.isCardReadOnly(id)
    /** Future native receive entry: design and immutable provenance are written in one transaction. */
    fun receive(card:PolaroidCard,albumId:String=DEFAULT_ALBUM):PolaroidCard=synchronized(stickers) {
        stickerRequire(cards.value.none {it.id==card.id},message="拍立得已存在")
        val edits=listOf(StickerEdit.PutCard(card))+if(albumId==DEFAULT_ALBUM)emptyList() else listOf(StickerEdit.MoveAlbumCards(albumId,listOf(card.id)))
        stickers.applyReceived(edits,setOf(card.id));card
    }
    fun copy(card:PolaroidCard):PolaroidCard=synchronized(stickers) {
        val source=cards.value.firstOrNull {it.id==card.id} ?: throw StickerFault(StickerError.NOT_FOUND,"拍立得不存在")
        val duplicate=source.copy(id=UUID.randomUUID().toString(),title=if(source.title.isBlank())"" else (source.title+" 副本").take(128))
        val album=stickers.albums.value.firstOrNull {source.id in it.cardIds}?.id ?: DEFAULT_ALBUM
        if(isReadOnly(source.id))receive(duplicate,album) else create(duplicate,album)
    }
    fun createAlbum(name:String,id:String=UUID.randomUUID().toString()):PolaroidAlbum=synchronized(stickers) {
        stickerRequire(albums.value.none {it.id==id},message="相册已存在")
        stickers.apply(listOf(StickerEdit.PutAlbum(id,name.trim()))).albums.first {it.id==id}
    }
    fun renameAlbum(id:String,name:String):PolaroidAlbum=synchronized(stickers) {
        stickerRequire(albums.value.any {it.id==id},StickerError.NOT_FOUND,"相册不存在")
        stickers.apply(listOf(StickerEdit.PutAlbum(id,name.trim()))).albums.first {it.id==id}
    }
    fun removeAlbum(id:String) {stickers.apply(listOf(StickerEdit.RemoveAlbums(listOf(id))))}
    fun removeAlbums(ids:List<String>) {stickers.apply(listOf(StickerEdit.RemoveAlbums(ids)))}
    fun moveCards(ids:List<String>,albumId:String) {stickers.apply(listOf(StickerEdit.MoveAlbumCards(albumId,ids)))}
    fun orderAlbums(ids:List<String>) {stickers.apply(listOf(StickerEdit.OrderAlbums(ids)))}
    fun orderCards(albumId:String,ids:List<String>) {stickers.apply(listOf(StickerEdit.OrderAlbumCards(albumId,ids)))}
    /** IDs are deterministic so an interrupted migration never duplicates old cards. */
    suspend fun migrateLegacy()=withContext(Dispatchers.IO) {
        synchronized(stickers) {
            if(stickers.legacyMigrated)return@synchronized
            val legacy=CardStore(context).load()
            val cards=legacy.mapIndexedNotNull {index,old->
                val id=UUID.nameUUIDFromBytes("legacy-card/$index/${old.title}/${old.description}".toByteArray(Charsets.UTF_8)).toString()
                if(this@CardRepository.cards.value.any {it.id==id})null else {
                    val image=old.imageUri?.let {uri->runCatching {stickers.importImage(Uri.parse(uri)).id}.getOrNull()}
                    PolaroidCard(id,old.title.take(128),old.description.take(8192),image,frameStyleId="classic")
                }
            }
            cards.chunked(100).forEach {batch->stickers.apply(batch.map {StickerEdit.PutCard(it)})}
            stickers.markMigrated()
        }
    }
    companion object {const val DEFAULT_ALBUM=DEFAULT_POLAROID_ALBUM_ID}
}
