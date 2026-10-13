package com.kigtts.next.cards

import com.kigtts.plugin.api.*

/** Album membership and its order are changed in the same transaction as the photo catalog. */
internal object PolaroidAlbums {
    fun migrate(value:StickerSnapshot):StickerSnapshot = if(value.albums.isEmpty()) value.copy(albums=listOf(
        PolaroidAlbum(DEFAULT_POLAROID_ALBUM_ID,"默认相册",value.cards.map {it.id}))) else value

    fun validate(value:StickerSnapshot) {
        val albums=value.albums
        stickerRequire(albums.size in 1..128,StickerError.TOO_LARGE,"相册数量超出上限")
        stickerRequire(albums.map {it.id}.distinct().size==albums.size&&albums.any {it.id==DEFAULT_POLAROID_ALBUM_ID},message="相册标识重复或缺少默认相册")
        val cards=value.cards.map {it.id}.toSet()
        albums.forEach {album->
            stickerRequire(album.id.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{0,95}")),message="相册标识不合法")
            stickerRequire(album.name.isNotBlank()&&album.name.length<=128&&!album.name.contains('\u0000'),message="相册名称不合法")
            stickerRequire(album.cardIds.size<=256&&album.cardIds.distinct().size==album.cardIds.size,StickerError.INVALID,"相册中的照片不能重复")
            stickerRequire(album.cardIds.all {it in cards},StickerError.NOT_FOUND,"相册中的照片不存在")
        }
        val assigned=albums.flatMap {it.cardIds}
        stickerRequire(assigned.size==cards.size&&assigned.toSet()==cards,message="每张拍立得必须属于一个相册")
    }

    fun apply(before:StickerSnapshot,edits:List<StickerEdit>):List<PolaroidAlbum> {
        val albums=migrate(before).albums.toMutableList()
        val cards=before.cards.map {it.id}.toMutableSet()
        fun album(id:String):PolaroidAlbum = albums.firstOrNull {it.id==id} ?: throw StickerFault(StickerError.NOT_FOUND,"相册不存在")
        fun put(value:PolaroidAlbum) {val index=albums.indexOfFirst {it.id==value.id};if(index<0)albums.add(value) else albums[index]=value}
        fun ids(values:List<String>,all:Set<String>,message:String) {
            stickerRequire(values.isNotEmpty()&&values.distinct().size==values.size,message="请选择不重复的照片或相册")
            stickerRequire(values.all {it in all},StickerError.NOT_FOUND,message)
        }
        edits.forEach {edit->when(edit) {
            is StickerEdit.PutCard->if(cards.add(edit.card.id)) {
                val target=album(DEFAULT_POLAROID_ALBUM_ID);put(target.copy(cardIds=target.cardIds+edit.card.id))
            }
            is StickerEdit.RemoveCards->{cards.removeAll(edit.ids.toSet());albums.replaceAll {it.copy(cardIds=it.cardIds.filterNot {id->id in edit.ids})}}
            is StickerEdit.OrderCards->albums.replaceAll {current->current.copy(cardIds=edit.ids.filter {it in current.cardIds})}
            is StickerEdit.PutAlbum->{val old=albums.firstOrNull {it.id==edit.id};put(old?.copy(name=edit.name) ?: PolaroidAlbum(edit.id,edit.name))}
            is StickerEdit.RemoveAlbums->{
                ids(edit.ids,albums.map {it.id}.toSet(),"相册不存在")
                stickerRequire(DEFAULT_POLAROID_ALBUM_ID !in edit.ids,message="默认相册不能删除")
                val retained=album(DEFAULT_POLAROID_ALBUM_ID)
                val moved=albums.filter {it.id in edit.ids}.flatMap {it.cardIds}
                albums.removeAll {it.id in edit.ids};put(retained.copy(cardIds=retained.cardIds+moved))
            }
            is StickerEdit.MoveAlbumCards->{
                ids(edit.cardIds,cards,"待移动的拍立得不存在");val target=album(edit.albumId)
                val incoming=edit.cardIds.filterNot {it in target.cardIds}
                albums.replaceAll {if(it.id==target.id)it.copy(cardIds=it.cardIds+incoming)
                    else it.copy(cardIds=it.cardIds.filterNot {id->id in edit.cardIds})}
            }
            is StickerEdit.OrderAlbums->{
                stickerRequire(edit.ids.size==albums.size&&edit.ids.toSet()==albums.map {it.id}.toSet(),message="相册排序必须包含每一册且不能重复")
                val indexed=albums.associateBy {it.id};albums.clear();albums.addAll(edit.ids.map {indexed.getValue(it)})
            }
            is StickerEdit.OrderAlbumCards->{
                val target=album(edit.albumId)
                stickerRequire(edit.cardIds.size==target.cardIds.size&&edit.cardIds.toSet()==target.cardIds.toSet(),message="照片排序必须包含相册每一张照片且不能重复")
                put(target.copy(cardIds=edit.cardIds))
            }
            else->Unit
        }}
        return albums
    }
}
