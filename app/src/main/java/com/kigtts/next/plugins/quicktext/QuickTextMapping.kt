package com.kigtts.next.plugins.quicktext

import com.kigtts.next.state.*
import com.kigtts.next.media.*
import com.kigtts.next.text.*
import com.kigtts.plugin.api.*

internal fun QuickTextLibrary.groups()=categories.value.map { QuickGroup(requireNotNull(categoryIds.value[it]),it,categoryIcons.value.getValue(it).name) }
internal fun QuickPhrase.dto(groups:List<QuickGroup>,media:MediaEntry?)=QuickEntry(id,groups.first {it.name==category}.id,text,title,
    QuickCardKind.valueOf(cardType(media?.kind).name),mediaId,QuickColorKind.valueOf(colorMode.name),color,richText,
    formats.map { QuickFormat(it.start,it.end,QuickFormatKind.valueOf(it.kind.name),it.value) },autoPlay,audioMode?.let {QuickAudioPolicy.valueOf(it.name)},metadata)
internal fun QuickEntry.native(groups:List<QuickGroup>)=QuickPhrase(text,groups.first {it.id==groupId}.name,id,title,mediaId,
    QuickCardType.valueOf(kind.name),CardColorMode.valueOf(colorKind.name),color,richText,
    formats.map {TextFormatRun(it.start,it.end,TextFormat.valueOf(it.kind.name),it.value)},autoPlay,audioPolicy?.let {MediaAudioMode.valueOf(it.name)},metadata)
internal fun MediaEntry.dto()=QuickMedia(id,kind.name,pluginId,created,if(kind==MediaKind.HTML)projectRoot?.let {primary.removePrefix("$it/")} ?: primary.substringAfterLast('/') else null,
    scale.name,network,mimeType,if(kind==MediaKind.WEB)primary else null,thumbnailMode.name,focusX,focusY,video!=null,appleLive)
internal class QuickTextFault(val code:QuickTextError,message:String):IllegalArgumentException(message)
internal fun requireQuick(value:Boolean,code:QuickTextError=QuickTextError.INVALID,message:String) {if(!value)throw QuickTextFault(code,message)}
