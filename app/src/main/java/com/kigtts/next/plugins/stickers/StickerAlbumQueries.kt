package com.kigtts.next.plugins.stickers

import com.kigtts.next.cards.StickerRepository
import com.kigtts.next.cards.stickerRequire
import com.kigtts.plugin.api.*

internal object StickerAlbumQueries {
    private fun next(offset:Int,limit:Int,total:Int):Int?=(offset.toLong()+limit).takeIf {it<total.toLong()}?.toInt()
    private fun snapshot(repository:StickerRepository,offset:Int,limit:Int,revision:Long?):StickerSnapshot = synchronized(repository) {
        stickerRequire(offset>=0&&limit in 1..50,message="分页参数不合法")
        revision?.let {repository.checkRevision(it)};repository.snapshot.value
    }
    fun albums(repository:StickerRepository,command:StickerCommand.QueryAlbums):StickerReply.Albums {
        val value=snapshot(repository,command.offset,command.limit,command.revision)
        return StickerReply.Albums(value.revision,value.albums.drop(command.offset).take(command.limit),value.albums.size,
            next(command.offset,command.limit,value.albums.size))
    }
    fun cards(repository:StickerRepository,command:StickerCommand.QueryAlbumCards):StickerReply.Snapshot {
        val value=snapshot(repository,command.offset,command.limit,command.revision)
        val album=value.albums.firstOrNull {it.id==command.albumId}
        stickerRequire(album!=null,StickerError.NOT_FOUND,"相册不存在")
        val selected=requireNotNull(album);val indexed=value.cards.associateBy {it.id}
        val photos=selected.cardIds.drop(command.offset).take(command.limit).map {indexed.getValue(it)}
        return StickerReply.Snapshot(StickerSnapshot(value.revision,cards=photos,offset=command.offset,total=selected.cardIds.size,
            nextOffset=next(command.offset,command.limit,selected.cardIds.size),albums=listOf(selected)))
    }
}
