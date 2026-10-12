package com.kigtts.next.plugins.quicktext

import com.kigtts.next.state.*
import com.kigtts.next.media.*
import com.kigtts.plugin.api.*
import java.util.UUID

internal data class QuickPlan(val owner:String,val transactionId:String,val fingerprint:String,val preview:QuickBatchPreview,
    val groups:List<QuickGroup>,val entries:List<QuickEntry>,val expires:Long=System.currentTimeMillis()+600_000,
    var approved:Boolean=false,val cleanup:()->Unit={},var mapping:PluginResource?=null)

/** Build a complete candidate before touching persistent state. Reordering always names a full set. */
internal fun planBatch(owner:String,command:QuickTextCommand.Prepare,originalGroups:List<QuickGroup>,original:List<QuickEntry>,
    revision:Long,media:Map<String,MediaKind>,maxOperations:Int=100):QuickPlan {
    requireQuick(command.transactionId.matches(Regex("[A-Za-z0-9_-]{1,80}")),message="事务标识不合法")
    requireQuick(command.revision==revision,QuickTextError.CONFLICT,"快捷文本已更新，请重新读取")
    requireQuick(command.edits.size in 1..maxOperations,message="批次大小不合法")
    val groups=originalGroups.toMutableList();val entries=original.toMutableList()
    fun ids(values:List<String>) {requireQuick(values.distinct().size==values.size&&values.size<=10_000,message="标识重复或数量过多")}
    command.edits.forEach {edit->when(edit) {
        is QuickTextEdit.PutGroup->{val group=edit.group
            requireQuick(group.id.matches(Regex("[a-f0-9-]{36}"))&&group.name.isNotBlank()&&group.name==group.name.trim()&&group.name.length<=80,message="分组名称或标识不合法")
            requireQuick(CategoryIcon.entries.any {it.name==group.icon}&&groups.none {it.id!=group.id&&it.name==group.name},message="分组图标或名称不合法")
            val at=groups.indexOfFirst {it.id==group.id};if(at<0)groups+=group else groups[at]=group
        }
        is QuickTextEdit.PutEntry->{val item=edit.entry
            requireQuick(item.id.matches(Regex("[a-f0-9-]{36}"))&&groups.any {it.id==item.groupId},message="条目标识或分组不合法")
            requireQuick(item.text.isNotBlank()&&item.text.toByteArray(Charsets.UTF_8).size<=128*1024&&item.title.length<=256,message="请填写字幕，或缩短过长内容")
            requireQuick(item.color in 0..0xFFFFFFFFL&&item.formats.size<=256&&item.formats.all {it.start>=0&&it.end>it.start&&it.end<=item.text.length&&
                (it.kind!=QuickFormatKind.SIZE||it.value in 75..400)},message="颜色或富文本范围不合法")
            requireQuick(item.metadata.size<=32&&item.metadata.all {it.key.length in 1..128&&it.value.toByteArray().size<=4096}&&
                item.metadata.values.sumOf {it.toByteArray().size}<=16*1024,message="扩展元数据过大")
            val attachment=media[item.mediaId]
            requireQuick(when(item.kind) {QuickCardKind.TEXT->item.mediaId==null;QuickCardKind.AUDIO->attachment==MediaKind.AUDIO;
                QuickCardKind.MEDIA->attachment in setOf(MediaKind.IMAGE,MediaKind.MOTION_PHOTO,MediaKind.VIDEO);
                QuickCardKind.PAGE->attachment in setOf(MediaKind.HTML,MediaKind.WEB)},message="附件不存在或类型不匹配")
            val at=entries.indexOfFirst {it.id==item.id};if(at<0)entries+=item else entries[at]=item
        }
        is QuickTextEdit.RemoveEntries->{ids(edit.ids);requireQuick(edit.ids.all {id->entries.any {it.id==id}},QuickTextError.NOT_FOUND,"条目不存在");entries.removeAll {it.id in edit.ids}}
        is QuickTextEdit.RemoveGroups->{ids(edit.ids);requireQuick(edit.ids.all {id->groups.any {it.id==id}},QuickTextError.NOT_FOUND,"分组不存在")
            requireQuick(edit.ids.size<groups.size,message="至少保留一个分组")
            val contained=entries.filter {it.groupId in edit.ids}
            requireQuick(contained.isEmpty()||edit.deleteEntries||edit.moveTo!=null,message="非空分组需要指定移动目标或确认删除条目")
            if(edit.moveTo!=null)requireQuick(edit.moveTo !in edit.ids&&groups.any {it.id==edit.moveTo},message="移动目标分组不存在")
            if(edit.deleteEntries)entries.removeAll {it.groupId in edit.ids}
            else edit.moveTo?.let {destination->entries.replaceAll {if(it.groupId in edit.ids)it.copy(groupId=destination) else it}}
            groups.removeAll {it.id in edit.ids}
        }
        is QuickTextEdit.MoveEntries->{ids(edit.ids);requireQuick(groups.any {it.id==edit.groupId}&&edit.ids.all {id->entries.any {it.id==id}},message="条目或目标分组不存在")
            val moved=edit.ids.map {id->entries.first {it.id==id}.copy(groupId=edit.groupId)};entries.removeAll {it.id in edit.ids}
            val at=edit.beforeId?.let {target->entries.indexOfFirst {it.id==target&&it.groupId==edit.groupId}.also {requireQuick(it>=0,message="目标条目不存在")}} ?: entries.size
            entries.addAll(at,moved)
        }
        is QuickTextEdit.OrderEntries->{ids(edit.ids);val current=entries.filter {it.groupId==edit.groupId}
            requireQuick(groups.any {it.id==edit.groupId}&&current.map {it.id}.toSet()==edit.ids.toSet(),message="排序需要包含分组中全部条目")
            val sorted=edit.ids.map {id->current.first {it.id==id}};var index=0;entries.replaceAll {if(it.groupId==edit.groupId)sorted[index++] else it}
        }
        is QuickTextEdit.OrderGroups->{ids(edit.ids);requireQuick(groups.map {it.id}.toSet()==edit.ids.toSet(),message="排序需要包含全部分组")
            val sorted=edit.ids.map {id->groups.first {it.id==id}};groups.clear();groups.addAll(sorted)
        }
    } }
    requireQuick(groups.size<=256&&entries.size<=10_000,message="分组或条目数量过多")
    val deleted=original.count {old->entries.none {it.id==old.id}}
    val groupDeleted=originalGroups.any {old->groups.none {it.id==old.id}}
    val changed=entries.filter {item->original.firstOrNull {it.id==item.id}!=item}
    val preview=QuickBatchPreview(UUID.randomUUID().toString(),command.revision,changed.count {item->original.none {it.id==item.id}},
        changed.count {item->original.any {it.id==item.id}},deleted,(groups.map {it.id}+originalGroups.map {it.id}).distinct().count {id->groups.firstOrNull {it.id==id}!=originalGroups.firstOrNull {it.id==id}},
        (changed.map {it.title.ifBlank {it.text.take(60)}}+original.filter {old->entries.none {it.id==old.id}}.map {it.title.ifBlank {it.text.take(60)}}+
            groups.filter {it !in originalGroups}.map {"分组："+it.name}+originalGroups.filter {old->groups.none {it.id==old.id}}.map {"删除分组："+it.name}).take(100),deleted>0||groupDeleted)
    return QuickPlan(owner,command.transactionId,PluginJson.encodeToString(command),preview,groups,entries)
}
