package com.kigtts.next.cards

import com.kigtts.plugin.api.*

/** Original host frames and a resource-backed registry; IDs outlive missing optional styles. */
internal object PolaroidFrames {
    private val legacy=listOf(PolaroidFrameStyle("classic","经典"),PolaroidFrameStyle("night","深色",color=0xFF24282A),
        PolaroidFrameStyle("slim","细框",color=0xFFF8F7F3,borderColor=0xFF777777,borderWidth=.003333),
        PolaroidFrameStyle("soft","柔色",color=0xFFF2E6EB,photoCornerRadius=.024))
    val defaults=legacy+listOf(PolaroidFormat.MINI,PolaroidFormat.SQUARE,PolaroidFormat.WIDE).flatMap {format->
        legacy.map {it.copy(id="instax.${format.name.lowercase()}.${it.id}",format=format)}}
    val reserved=defaults.map {it.id}.toSet()
    fun apply(before:List<PolaroidFrameStyle>,edits:List<StickerEdit>):List<PolaroidFrameStyle> {
        val styles=before.toMutableList()
        edits.forEach {edit->when(edit) {
            is StickerEdit.PutFrameStyle->{stickerRequire(edit.style.id !in reserved,message="内置边框不能改写")
                val index=styles.indexOfFirst {it.id==edit.style.id};val next=edit.style.copy(version=if(index<0)1 else styles[index].version+1)
                if(index<0)styles.add(next) else styles[index]=next}
            is StickerEdit.RemoveFrameStyles->{stickerRequire(edit.ids.isNotEmpty()&&edit.ids.distinct().size==edit.ids.size&&edit.ids.none {it in reserved},message="请选择可移除的自定义边框")
                stickerRequire(edit.ids.all {id->styles.any {it.id==id}},StickerError.NOT_FOUND,"边框不存在");styles.removeAll {it.id in edit.ids}}
            else->Unit
        }}
        return styles
    }
    fun validate(snapshot:StickerSnapshot,styles:List<PolaroidFrameStyle>) {
        stickerRequire(styles.size in defaults.size..(defaults.size+128)&&styles.map {it.id}.distinct().size==styles.size,StickerError.TOO_LARGE,"边框数量过多或标识重复")
        stickerRequire(defaults.all {builtIn->styles.any {it==builtIn}},message="内置边框不完整")
        val images=snapshot.images.map {it.id}.toSet()
        styles.forEach {style->stickerRequire(style.id.matches(Regex("[a-z][a-z0-9.-]{1,95}"))&&!style.id.contains("..")&&style.name.isNotBlank()&&style.name.length<=128&&style.version>0,message="边框标识或名称不合法")
            val color=style.color;val borderColor=style.borderColor
            stickerRequire(color==null||color in 0..0xFFFFFFFFL,message="边框颜色不合法")
            stickerRequire(borderColor==null||borderColor in 0..0xFFFFFFFFL,message="边线颜色不合法")
            stickerRequire(style.borderWidth.isFinite()&&style.borderWidth in 0.0..0.02&&style.photoCornerRadius.isFinite()&&style.photoCornerRadius in 0.0..0.1,message="边框边线或圆角不合法")
            stickerRequire(style.overlayImageId==null||style.overlayImageId in images,message="边框图片不存在")
            style.owner?.let {owner->stickerRequire(owner.matches(Regex("[a-z][a-z0-9.-]{1,79}"))&&!owner.contains("..")&&style.id.startsWith("$owner."),message="边框发布者与命名空间不匹配")}
        }
    }
}
