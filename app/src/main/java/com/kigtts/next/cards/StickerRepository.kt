package com.kigtts.next.cards

import android.content.Context
import android.net.Uri
import com.kigtts.next.media.MediaRepository
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PluginPaths
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** One durable owner for user galleries, image copies, and all polaroid designs. */
class StickerRepository(val context:Context,val paths:PluginPaths,val media:MediaRepository,
    private val catalogByteLimit:Int=8*1024*1024) {
    val root=File(context.getExternalFilesDir(null) ?: context.filesDir,"appdata/Kigtts/cards").apply {mkdirs()}
    val designs=PolaroidDesignStore(root)
    val identityFrames=com.kigtts.next.identity.IdentityFrameStore(this)
    val identityDesigns=com.kigtts.next.identity.IdentityDesignStore(context)
    internal val identityBackgrounds=com.kigtts.next.identity.IdentityBackgroundCache(this)
    private val catalog=File(root,"catalog.json")
    private val receipts=linkedMapOf<String,Pair<String,StickerReply>>()
    private val creationTimes=PolaroidCreationTimes()
    private val cardPermissions=PolaroidCardPermissions()
    internal var legacyMigrated=false;private set
    private val frameState=MutableStateFlow(PolaroidFrames.defaults)
    val frameStyles=frameState.asStateFlow()
    private val state=MutableStateFlow(load())
    val snapshot=state.asStateFlow()
    private val libraryState=MutableStateFlow(state.value.libraries)
    private val itemState=MutableStateFlow(state.value.items)
    private val imageState=MutableStateFlow(state.value.images)
    private val cardState=MutableStateFlow(state.value.cards)
    private val albumState=MutableStateFlow(state.value.albums)
    val libraries=libraryState.asStateFlow();val items=itemState.asStateFlow()
    val images=imageState.asStateFlow();val cards=cardState.asStateFlow()
    val albums=albumState.asStateFlow()
    private val readOnlyState=MutableStateFlow(cardPermissions.current())
    val readOnlyCards=readOnlyState.asStateFlow()
    private val resources=StickerImages(this)

    private fun load():StickerSnapshot {
        if(!catalog.isFile)return empty()
        return runCatching {
            require(catalog.length()<=catalogByteLimit)
            val json=JSONObject(catalog.readText(Charsets.UTF_8))
            val snapshotJson=JSONObject(json.getString("snapshot"))
            snapshotJson.optJSONArray("cards")?.let {cards->for(index in 0 until cards.length()) {
                val card=cards.getJSONObject(index);if(!card.has("frameStyleId"))card.put("frameStyleId","classic")}}
            val stored=PluginJson.decodeFromString<StickerSnapshot>(snapshotJson.toString())
            val value=PolaroidAlbums.migrate(stored.copy(cards=stored.cards.map {designs.normalizeFields(it.normalizeLegacyPaper())}))
            StickerEdits.validate(value)
            val savedFrames=json.optString("frames").takeIf {it.isNotBlank()}?.let {PluginJson.decodeFromString<List<PolaroidFrameStyle>>(it)}.orEmpty()
            // Host style revisions must not turn an otherwise valid user catalog into recovery data.
            val frames=PolaroidFrames.defaults+savedFrames.filterNot {it.id in PolaroidFrames.reserved}
            PolaroidFrames.validate(value,frames)
            legacyMigrated=json.optBoolean("legacyMigrated")
            json.optJSONObject("receipts")?.let {all->all.keys().asSequence().take(256).forEach {key->
                val item=all.getJSONObject(key);receipts[key]=item.getString("hash") to PluginJson.decodeFromString(item.getString("reply"))}}
            creationTimes.load(if(json.optInt("cardCreatedAtSchema",0)==1)json.optJSONObject("cardCreatedAt") else null,value.cards)
            cardPermissions.load(json)
            frameState.value=frames;value
        }.getOrElse {
            receipts.clear();legacyMigrated=false
            // Preserve the damaged catalog for recovery rather than destroying its only copy.
            catalog.copyTo(File(root,"catalog-recovery-${System.currentTimeMillis()}.json"),overwrite=false)
            empty()
        }
    }
    private fun empty()=PolaroidAlbums.migrate(StickerSnapshot(0,libraries=listOf(StickerLibrary(DEFAULT_LIBRARY,"我的贴纸"))))
    @Synchronized internal fun receipt(key:String)=receipts[key]
    @Synchronized internal fun record(key:String,hash:String,reply:StickerReply) {
        val previous=LinkedHashMap(receipts);receipts[key]=hash to reply
        while(receipts.size>256)receipts.remove(receipts.keys.first())
        try {persist(state.value)} catch(error:Exception) {receipts.clear();receipts.putAll(previous);throw error}
    }
    @Synchronized fun plan(edits:List<StickerEdit>,expectedRevision:Long?=null):StickerSnapshot {
        expectedRevision?.let {checkRevision(it)}
        val frames=framePlan(edits)
        stickerRequire(designs.referencedFrames().all { id -> frames.any { it.id==id } },message="边框仍用于相框预设，请先删除对应预设")
        return StickerEdits.apply(state.value,edits).also {
            val remaining = it.images.map { image -> image.id }.toSet()
            stickerRequire(identityFrames.items.value.mapNotNull { frame -> frame.imageId }.all { it in remaining }, message="图片仍用于名片边框")
            stickerRequire(identityDesigns.items.value.all { card ->
                listOfNotNull(card.imageId, card.originalImageId, card.photoStyle?.lutImageId).plus(card.layers.mapNotNull { layer -> layer.imageId }).all { image -> image in remaining }
            }, message="图片仍用于个人名片，请先更换名片背景或贴纸")
            stickerRequire(designs.referencedImages().all { image -> image in remaining }, message="图片仍用于相框预设，请先删除对应预设")
            PolaroidFrames.validate(it,frames)
            cardPermissions.check(state.value,it,frameState.value,frames)}
    }
    @Synchronized fun framePlan(edits:List<StickerEdit>)=PolaroidFrames.apply(frameState.value,edits)
    fun frameStyle(card:PolaroidCard)=photoFrame(card,frameState.value)
    @Synchronized fun apply(edits:List<StickerEdit>,expectedRevision:Long?=null):StickerSnapshot {
        val planned=plan(edits,expectedRevision)
        return replace(planned,state.value.revision,frames=framePlan(edits))
    }
    @Synchronized internal fun replace(value:StickerSnapshot,expectedRevision:Long,receipt:Triple<String,String,StickerReply>?=null,
        tokenReceipt:Triple<String,String,StickerReply>?=null,frames:List<PolaroidFrameStyle> = frameState.value,
        readOnlyAdditions:Set<String> = emptySet()):StickerSnapshot {
        checkRevision(expectedRevision);StickerEdits.validate(value);PolaroidFrames.validate(value,frames)
        cardPermissions.check(state.value,value,frameState.value,frames)
        val permissions=cardPermissions.plan(readOnlyAdditions,value.cards)
        val next=value.copy(revision=state.value.revision+1,offset=0,total=0,nextOffset=null)
        val before=LinkedHashMap(receipts)
        listOfNotNull(receipt,tokenReceipt).forEach {(key,hash,reply)->receipts[key]=hash to reply}
        while(receipts.size>256)receipts.remove(receipts.keys.first())
        try {persist(next,frames,permissions)} catch(error:Exception) {receipts.clear();receipts.putAll(before);throw error}
        val removed=state.value.images.filter {old->next.images.none {it.id==old.id}}
        frameState.value=frames;publish(next)
        removed.forEach {image->val directory=File(root,"images/${image.id}").canonicalFile
            if(directory.toPath().startsWith(File(root,"images").canonicalFile.toPath()))runCatching {directory.deleteRecursively()}}
        return next
    }
    @Synchronized internal fun addImage(image:StickerImage):StickerImage {
        val value=state.value
        stickerRequire(value.images.size<4096&&value.images.sumOf {it.bytes}+image.bytes<=2L*1024*1024*1024,StickerError.TOO_LARGE,"图库空间不足")
        replace(value.copy(images=value.images+image),value.revision);return image
    }
    @Synchronized internal fun markMigrated() {val before=legacyMigrated;legacyMigrated=true
        try {persist(state.value)} catch(error:Exception) {legacyMigrated=before;throw error}}
    internal fun checkRevision(revision:Long)=stickerRequire(revision==state.value.revision,StickerError.CONFLICT,"内容已更新，请重新读取")
    private fun publish(value:StickerSnapshot) {
        readOnlyState.value=cardPermissions.current()
        libraryState.value=value.libraries;itemState.value=value.items;imageState.value=value.images;cardState.value=value.cards;albumState.value=value.albums;state.value=value
    }
    private fun persist(value:StickerSnapshot,frames:List<PolaroidFrameStyle> = frameState.value,
        permissions:Set<String> = cardPermissions.current()) {
        val dates=creationTimes.plan(state.value.cards,value.cards)
        val data=JSONObject().put("schema",1).put("snapshot",PluginJson.encodeToString(value)).put("legacyMigrated",legacyMigrated)
        data.put("cardCreatedAtSchema",1).put("cardCreatedAt",creationTimes.json(dates))
        cardPermissions.write(data,permissions)
        data.put("frames",PluginJson.encodeToString(frames.filterNot {it.id in PolaroidFrames.reserved}))
        data.put("receipts",JSONObject().also {json->receipts.forEach {(key,item)->json.put(key,JSONObject().put("hash",item.first).put("reply",PluginJson.encodeToString(item.second)))}})
        val encoded=data.toString()
        stickerRequire(encoded.toByteArray(Charsets.UTF_8).size<=catalogByteLimit,StickerError.TOO_LARGE,"图库和拍立得内容过多，请减少文字或图层后重试")
        val stage=File(root,".catalog.tmp")
        try {stage.writeText(encoded,Charsets.UTF_8);Files.move(stage.toPath(),catalog.toPath(),StandardCopyOption.REPLACE_EXISTING)} finally {stage.delete()}
        creationTimes.commit(dates)
        cardPermissions.commit(permissions)
    }
    @Synchronized fun isCardReadOnly(id:String):Boolean=cardPermissions.isReadOnly(id)
    /** Reserved for a native receiving flow; no plugin command can remove or assign provenance. */
    @Synchronized fun markCardReadOnly(id:String) {
        stickerRequire(state.value.cards.any {it.id==id},StickerError.NOT_FOUND,"拍立得不存在")
        if(!cardPermissions.isReadOnly(id))replace(state.value,state.value.revision,readOnlyAdditions=setOf(id))
    }
    @Synchronized internal fun applyReceived(edits:List<StickerEdit>,ids:Set<String>):StickerSnapshot {
        val planned=plan(edits)
        return replace(planned,state.value.revision,frames=framePlan(edits),readOnlyAdditions=ids)
    }
    @Synchronized fun cardCreatedAt(id:String):Long=creationTimes.get(id)
    fun importImage(uri:Uri):StickerImage=resources.import(uri)
    suspend fun importPhoto(uri:Uri):StickerImage=resources.import(openPhotoSource(context,uri))
    fun importMedia(mediaId:String):StickerImage=resources.importMedia(mediaId)
    fun importResource(source:File):StickerImage=resources.import(source.inputStream())
    fun imageFile(id:String):File?=resources.file(id)
    fun publishQuickMedia(imageId:String):QuickMedia=resources.quickMedia(imageId)
    fun exportCard(cardId:String):File=PolaroidRenderer.export(this,requireNotNull(cards.value.firstOrNull {it.id==cardId}) {"拍立得不存在"})
    /** Render a validated editor draft without committing it or changing the catalog revision. */
    fun exportCard(card:PolaroidCard):File {plan(listOf(StickerEdit.PutCard(card)));return PolaroidRenderer.export(this,card)}
    companion object {const val DEFAULT_LIBRARY="user.stickers"}
}

internal class StickerFault(val code:StickerError,message:String):IllegalArgumentException(message)
internal fun stickerRequire(value:Boolean,code:StickerError=StickerError.INVALID,message:String) {if(!value)throw StickerFault(code,message)}
