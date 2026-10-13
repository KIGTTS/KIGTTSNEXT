package com.kigtts.next.cards

import com.kigtts.plugin.api.*

internal object StickerEdits {
    private val idPattern=Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{0,95}")
    private fun id(value:String) {stickerRequire(value.matches(idPattern),message="标识不合法")}
    private fun name(value:String,max:Int=128) {stickerRequire(value.isNotBlank()&&value.length<=max&&!value.contains('\u0000'),message="名称不合法")}
    fun validate(value:StickerSnapshot) {
        stickerRequire(value.revision>=0&&value.libraries.size in 1..128&&value.items.size<=2048&&value.images.size<=4096&&value.cards.size<=256,StickerError.TOO_LARGE,"内容数量超出上限")
        fun unique(ids:List<String>) {ids.forEach(::id);stickerRequire(ids.toSet().size==ids.size,message="标识重复")}
        unique(value.libraries.map {it.id});unique(value.items.map {it.id});unique(value.images.map {it.id});unique(value.cards.map {it.id})
        value.libraries.forEach {name(it.name)}
        val libraries=value.libraries.map {it.id}.toSet();val images=value.images.map {it.id}.toSet()
        value.images.forEach {stickerRequire(it.mime.startsWith("image/")&&it.bytes in 1..StickerImages.MAX_BYTES&&it.width in 1..32768&&it.height in 1..32768&&it.width.toLong()*it.height<=80_000_000,message="图片信息不合法")}
        value.items.forEach {name(it.name);stickerRequire(it.libraryId in libraries&&it.imageId in images,message="贴纸的图库或图片不存在")}
        value.cards.forEach {card->name(card.title);stickerRequire(card.description.length<=8192&&card.layers.size<=64&&card.frameColor in 0..0xFFFFFFFFL,message="拍立得内容过大或颜色不合法")
            stickerRequire(card.photoX.isFinite()&&card.photoY.isFinite()&&card.photoZoom.isFinite()&&card.photoX in 0.0..1.0&&card.photoY in 0.0..1.0&&card.photoZoom in 1.0..8.0,message="照片构图位置或缩放不合法")
            stickerRequire(card.frameStyleId.matches(Regex("[a-z][a-z0-9.-]{1,95}"))&&!card.frameStyleId.contains(".."),message="边框标识不合法")
            stickerRequire(card.imageId==null||card.imageId in images,message="拍立得图片不存在")
            stickerRequire(card.originalImageId==null||card.originalImageId in images,message="拍立得原始图片不存在");unique(card.layers.map {it.id})
            card.layers.forEach {layer->stickerRequire(layer.x.isFinite()&&layer.y.isFinite()&&layer.width.isFinite()&&layer.rotation.isFinite()&&layer.x in 0.0..1.0&&layer.y in 0.0..1.0&&layer.width in .01..2.0&&layer.rotation in -36000.0..36000.0&&layer.color in 0..0xFFFFFFFFL,message="图层位置或尺寸不合法")
                when(layer.kind) {CardLayerKind.IMAGE->stickerRequire(layer.imageId in images&&layer.text.toByteArray(Charsets.UTF_8).size<=2048,message="贴纸图片不存在或内容过长")
                    CardLayerKind.TEXT->stickerRequire(layer.imageId==null&&layer.text.isNotBlank()&&layer.text.length<=2048,message="贴纸文字不合法")}}
        }
    }
    fun apply(before:StickerSnapshot,edits:List<StickerEdit>):StickerSnapshot {
        stickerRequire(edits.size in 1..100,message="每批需包含 1 至 100 项修改")
        val libraries=before.libraries.toMutableList();val items=before.items.toMutableList();val cards=before.cards.toMutableList();val images=before.images.toMutableList()
        fun <T> put(list:MutableList<T>,value:T,key:(T)->String) {val index=list.indexOfFirst {key(it)==key(value)};if(index<0)list.add(value) else list[index]=value}
        fun <T> order(list:MutableList<T>,ids:List<String>,key:(T)->String) {stickerRequire(ids.size==list.size&&ids.toSet()==list.map(key).toSet(),message="排序必须包含每一项且不能重复")
            val indexed=list.associateBy(key);list.clear();list.addAll(ids.map {indexed.getValue(it)})}
        fun existing(ids:List<String>,all:List<String>) {stickerRequire(ids.isNotEmpty()&&ids.size==ids.toSet().size&&ids.all {it in all},StickerError.NOT_FOUND,"待移除的内容不存在")}
        edits.forEach {edit->when(edit) {
            is StickerEdit.PutLibrary->put(libraries,edit.library) {it.id}
            is StickerEdit.PutItem->put(items,edit.item) {it.id}
            is StickerEdit.PutCard->put(cards,edit.card) {it.id}
            is StickerEdit.PutFrameStyle,is StickerEdit.RemoveFrameStyles->Unit
            is StickerEdit.RemoveItems->{existing(edit.ids,items.map {it.id});items.removeAll {it.id in edit.ids}}
            is StickerEdit.RemoveCards->{existing(edit.ids,cards.map {it.id});cards.removeAll {it.id in edit.ids}}
            is StickerEdit.RemoveImages->{existing(edit.ids,images.map {it.id});images.removeAll {it.id in edit.ids}}
            is StickerEdit.RemoveLibraries->{existing(edit.ids,libraries.map {it.id});stickerRequire(edit.ids.size<libraries.size,message="至少保留一个图库")
                val children=items.filter {it.libraryId in edit.ids}
                if(children.isNotEmpty()) {
                    if(edit.deleteItems)items.removeAll {it.libraryId in edit.ids}
                    else {stickerRequire(edit.moveTo!=null&&edit.moveTo !in edit.ids&&libraries.any {it.id==edit.moveTo},message="请为图库中的贴纸指定去向")
                        items.replaceAll {if(it.libraryId in edit.ids)it.copy(libraryId=requireNotNull(edit.moveTo)) else it}}
                };libraries.removeAll {it.id in edit.ids}}
            is StickerEdit.OrderLibraries->order(libraries,edit.ids) {it.id}
            is StickerEdit.OrderCards->order(cards,edit.ids) {it.id}
            is StickerEdit.OrderItems->{val selected=items.filter {it.libraryId==edit.libraryId}.toMutableList();stickerRequire(libraries.any {it.id==edit.libraryId},StickerError.NOT_FOUND,"图库不存在")
                order(selected,edit.ids) {it.id};val iterator=selected.iterator();items.replaceAll {if(it.libraryId==edit.libraryId)iterator.next() else it}}
        }}
        return before.copy(libraries=libraries,items=items,cards=cards,images=images).also(::validate)
    }
}
