package com.kigtts.next.plugins.stickers

import com.kigtts.next.cards.*
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PluginPaths
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Connection-bound resource grants and atomic edits. Native review alone may approve removals. */
class StickerHost(val repository:StickerRepository,private val paths:PluginPaths,
    private val openUi:suspend (StickerUiTarget,String?)->Boolean={_,_->false},private val editingCard:()->String?={null}) {
    private data class Plan(val owner:String,val transaction:String,val hash:String,val preview:StickerPreview,
        val snapshot:StickerSnapshot,val frames:List<PolaroidFrameStyle>,val expires:Long,var approved:Boolean=false)
    private val pending=ConcurrentHashMap<String,Plan>()
    private val lock=Mutex();private val decisions=MutableStateFlow(0L)
    fun forPlugin(owner:String,capabilities:Set<String>):StickerBroker {
        paths.validateId(owner)
        return object:StickerBroker {
            override suspend fun request(command:StickerCommand):StickerReply=withContext(Dispatchers.IO) {lock.withLock {
                try {
                    val read=command is StickerCommand.Query||command is StickerCommand.QueryFrames||command==StickerCommand.Describe||command is StickerCommand.Transaction||command is StickerCommand.ReadImage||command is StickerCommand.PublishQuickMedia||command is StickerCommand.ExportCard
                    requireAccess(capabilities,read);retire()
                    when(command) {
                        StickerCommand.Describe->StickerReply.Features(writable="stickers-write" in capabilities,operations=operations)
                        is StickerCommand.Query->query(command)
                        is StickerCommand.QueryFrames->{stickerRequire(command.offset>=0&&command.limit in 1..50,message="分页参数不合法")
                            command.revision?.let {repository.checkRevision(it)};val styles=repository.frameStyles.value
                            StickerReply.Frames(repository.snapshot.value.revision,styles.drop(command.offset).take(command.limit),styles.size,
                                (command.offset+command.limit).takeIf {it<styles.size})}
                        is StickerCommand.Prepare->prepare(owner,command)
                        is StickerCommand.Commit->commit(owner,command.token)
                        is StickerCommand.Discard->{stickerRequire(completed(owner,command.token)==null,StickerError.CONFLICT,"修改已经完成，不能再丢弃")
                            val plan=owned(owner,command.token);discard(plan);StickerReply.Discarded(command.token)}
                        is StickerCommand.Transaction->repository.receipt(key(owner,command.transactionId))?.second
                            ?: pending.values.firstOrNull {it.owner==owner&&it.transaction==command.transactionId}?.let {StickerReply.Prepared(it.preview)}
                            ?: StickerReply.Failure(StickerError.NOT_FOUND,"没有此事务")
                        is StickerCommand.PublishImage->{stickerRequire(command.source.pluginId==null||command.source.pluginId==owner,StickerError.PERMISSION_DENIED,"只能发布本插件提供的图片")
                            val file=paths.storage(owner).file(command.source.path);stickerRequire(file.isFile&&file.length() in 1..32L*1024*1024,message="图片不存在或过大")
                            StickerReply.Image(repository.importResource(file))}
                        is StickerCommand.CreateQrCode->StickerReply.Image(repository.createQrCode(command.text))
                        is StickerCommand.ImportMedia->{stickerRequire("quick-text-read" in capabilities||"quick-text-write" in capabilities,StickerError.PERMISSION_DENIED,"读取快捷文本图片需要 quick-text-read 能力")
                            StickerReply.Image(repository.importMedia(command.mediaId))}
                        is StickerCommand.ReadImage->read(owner,command)
                        is StickerCommand.ExportCard->copy(owner,repository.exportCard(command.cardId),command.destination)
                        is StickerCommand.PublishQuickMedia->{stickerRequire("quick-text-write" in capabilities,StickerError.PERMISSION_DENIED,"发布快捷文本图片需要 quick-text-write 能力")
                            StickerReply.Media(repository.publishQuickMedia(command.imageId))}
                        is StickerCommand.OpenUi->{if(command.target==StickerUiTarget.REVIEW)owned(owner,requireNotNull(command.id))
                            if(command.target==StickerUiTarget.EDIT_CARD)stickerRequire(repository.cards.value.any {it.id==command.id},StickerError.NOT_FOUND,"拍立得不存在")
                            if(command.target==StickerUiTarget.LIBRARY&&command.id!=null)stickerRequire(repository.libraries.value.any {it.id==command.id},StickerError.NOT_FOUND,"图库不存在")
                            stickerRequire(withContext(Dispatchers.Main) {openUi(command.target,command.id)},StickerError.HOST_UNAVAILABLE,"请先打开 KIGTTS")
                            StickerReply.UiOpened(command.target)}
                    }
                } catch(cancel:CancellationException) {throw cancel}
                catch(error:StickerFault) {StickerReply.Failure(error.code,error.message.orEmpty(),repository.snapshot.value.revision)}
                catch(_:Exception) {StickerReply.Failure(StickerError.INVALID,"操作无法完成，请检查内容和资源",repository.snapshot.value.revision)}
            }}
            override fun changes(afterRevision:Long):Flow<StickerChange> {
                requireAccess(capabilities,true)
                return combine(repository.snapshot,decisions) {snapshot,decisions->StickerChange(snapshot.revision,decisions)}
                    .distinctUntilChanged().filter {it.revision>afterRevision||it.decisionsVersion>0||afterRevision<0}
            }
        }
    }
    private fun requireAccess(capabilities:Set<String>,read:Boolean) {
        stickerRequire("stickers-write" in capabilities||read&&"stickers-read" in capabilities,StickerError.PERMISSION_DENIED,"插件未声明所需的贴纸能力")
    }
    private fun query(command:StickerCommand.Query):StickerReply.Snapshot {
        stickerRequire(command.offset>=0&&command.limit in 1..50,message="分页参数不合法")
        val value=repository.snapshot.value;command.revision?.let {repository.checkRevision(it)}
        command.libraryId?.let {stickerRequire(value.libraries.any {library->library.id==it},StickerError.NOT_FOUND,"图库不存在")}
        val libraries=value.libraries.filter {command.libraryId==null||it.id==command.libraryId}
        val items=value.items.filter {command.libraryId==null||it.libraryId==command.libraryId}
        fun <T> page(all:List<T>,scope:StickerScope)=if(command.scope in setOf(StickerScope.ALL,scope))all.drop(command.offset).take(command.limit) else emptyList()
        val total=when(command.scope) {StickerScope.ALL->maxOf(libraries.size,items.size,value.images.size,value.cards.size)
            StickerScope.LIBRARIES->libraries.size;StickerScope.ITEMS->items.size;StickerScope.IMAGES->value.images.size;StickerScope.CARDS->value.cards.size}
        return StickerReply.Snapshot(StickerSnapshot(value.revision,page(libraries,StickerScope.LIBRARIES),page(items,StickerScope.ITEMS),
            page(value.images,StickerScope.IMAGES),page(value.cards,StickerScope.CARDS),command.offset,total,(command.offset+command.limit).takeIf {it<total}))
    }
    private fun prepare(owner:String,command:StickerCommand.Prepare):StickerReply {
        stickerRequire(command.transactionId.matches(Regex("[a-zA-Z0-9._-]{1,128}")),message="事务标识不合法")
        val hash=java.security.MessageDigest.getInstance("SHA-256").digest(PluginJson.encodeToString<StickerCommand>(command).toByteArray()).joinToString("") {"%02x".format(it)}
        repository.receipt(key(owner,command.transactionId))?.let {stickerRequire(it.first==hash,StickerError.CONFLICT,"事务标识已用于其它修改");return it.second}
        pending.values.firstOrNull {it.owner==owner&&it.transaction==command.transactionId}?.let {
            stickerRequire(it.hash==hash,StickerError.CONFLICT,"事务标识已用于其它修改");return StickerReply.Prepared(it.preview)}
        stickerRequire(pending.size<64&&pending.values.count {it.owner==owner}<4,StickerError.BUSY,"请先完成已有的预览")
        val edits=command.edits.map {edit->when(edit) {
            is StickerEdit.PutFrameStyle->{val old=repository.frameStyles.value.firstOrNull {it.id==edit.style.id}
                stickerRequire(edit.style.owner==null||edit.style.owner==owner,StickerError.PERMISSION_DENIED,"不能伪造边框发布者")
                stickerRequire(edit.style.id.startsWith("$owner.")&&(old==null||old.owner==owner),StickerError.PERMISSION_DENIED,"边框需使用本插件的命名空间")
                edit.copy(style=edit.style.copy(owner=owner))}
            is StickerEdit.RemoveFrameStyles->{stickerRequire(edit.ids.all {id->repository.frameStyles.value.firstOrNull {it.id==id}?.owner==owner},StickerError.PERMISSION_DENIED,"只能移除本插件发布的边框");edit}
            else->edit
        }}
        val before=repository.snapshot.value
        val (after,frames)=synchronized(repository) {repository.plan(edits,command.revision) to repository.framePlan(edits)};checkDraft(after,frames)
        val deleted=before.libraries.count {old->after.libraries.none {it.id==old.id}}+before.items.count {old->after.items.none {it.id==old.id}}+
            before.cards.count {old->after.cards.none {it.id==old.id}}+before.images.count {old->after.images.none {it.id==old.id}}+
            repository.frameStyles.value.count {old->frames.none {it.id==old.id}}
        val titles=command.edits.mapNotNull {when(it) {is StickerEdit.PutCard->it.card.title;is StickerEdit.PutItem->it.item.name;is StickerEdit.PutLibrary->it.library.name;
            is StickerEdit.PutFrameStyle->it.style.name;is StickerEdit.RemoveFrameStyles->"移除边框："+repository.frameStyles.value.filter {style->style.id in it.ids}.joinToString {style->style.name};else->null}}.take(100)
        val preview=StickerPreview(UUID.randomUUID().toString(),before.revision,titles,deleted,deleted>0)
        pending[preview.token]=Plan(owner,command.transactionId,hash,preview,after,frames,System.currentTimeMillis()+10*60*1000)
        return StickerReply.Prepared(preview)
    }
    private fun checkDraft(next:StickerSnapshot,frames:List<PolaroidFrameStyle> = repository.frameStyles.value) {
        editingCard()?.let {id->val old=repository.cards.value.firstOrNull {it.id==id}
            stickerRequire(next.cards.firstOrNull {it.id==id}==old&&frames.firstOrNull {it.id==old?.frameStyleId}==repository.frameStyles.value.firstOrNull {it.id==old?.frameStyleId},StickerError.BUSY,"这张拍立得正在编辑，请稍后重试")}
    }
    private fun owned(owner:String,token:String):Plan {
        val plan=pending[token] ?: throw StickerFault(StickerError.NOT_FOUND,"预览已失效，请重新准备")
        stickerRequire(plan.owner==owner,StickerError.PERMISSION_DENIED,"预览不属于本插件");return plan
    }
    private fun commit(owner:String,token:String):StickerReply {
        completed(owner,token)?.let {return it}
        val plan=owned(owner,token);repository.checkRevision(plan.preview.revision);checkDraft(plan.snapshot,plan.frames)
        if(plan.preview.requiresConfirmation&&!plan.approved)return StickerReply.Failure(StickerError.CONFIRMATION_REQUIRED,"请打开预览并确认移除",reviewToken=token)
        val reply=StickerReply.Applied(plan.preview.revision+1,plan.transaction)
        repository.replace(plan.snapshot,plan.preview.revision,Triple(key(owner,plan.transaction),plan.hash,reply),
            Triple("token/$token",owner+"\n"+(System.currentTimeMillis()+10*60*1000),reply),frames=plan.frames);pending.remove(token);decisions.value++
        return reply
    }
    private fun completed(owner:String,token:String):StickerReply? {
        val receipt=repository.receipt("token/$token") ?: return null
        val actual=receipt.first.substringBefore('\n');val expires=receipt.first.substringAfter('\n').toLongOrNull() ?: return null
        stickerRequire(actual==owner,StickerError.PERMISSION_DENIED,"预览不属于本插件")
        return receipt.second.takeIf {expires>System.currentTimeMillis()}
    }
    private fun discard(plan:Plan) {repository.record(key(plan.owner,plan.transaction),plan.hash,StickerReply.Discarded(plan.preview.token));pending.remove(plan.preview.token);decisions.value++}
    private fun retire() {pending.values.filter {it.expires<System.currentTimeMillis()}.toList().forEach(::discard)}
    private fun read(owner:String,command:StickerCommand.ReadImage):StickerReply.Resource {
        val source=repository.imageFile(command.imageId) ?: throw StickerFault(StickerError.NOT_FOUND,"贴纸图片不存在")
        return copy(owner,source,command.destination)
    }
    private fun copy(owner:String,source:File,destination:String):StickerReply.Resource {
        val parts=destination.split('/')
        val safe=parts.firstOrNull() in setOf("exports","tmp")||parts.take(2)==listOf("workspace","exports")||
            (parts.size>=5&&parts[0]=="projects"&&parts[2]=="data"&&parts[3] in setOf("exports","tmp"))
        stickerRequire(safe,StickerError.PERMISSION_DENIED,"图片副本请保存到 exports 或 tmp 目录")
        val target=paths.storage(owner).file(destination);stickerRequire(!target.exists(),message="导出位置已有文件，请换一个名称")
        target.parentFile!!.mkdirs();val stage=File(target.parentFile,".sticker-${UUID.randomUUID()}")
        try {source.copyTo(stage);Files.move(stage.toPath(),target.toPath())} finally {stage.delete()}
        return StickerReply.Resource(PluginResource(destination,owner),target.length())
    }
    fun preview(token:String):StickerPreview?=synchronized(pending) {pending[token]?.takeIf {it.expires>System.currentTimeMillis()}?.preview}
    fun reviewSnapshot(token:String):StickerSnapshot?=synchronized(pending) {pending[token]?.takeIf {it.expires>System.currentTimeMillis()}?.snapshot}
    suspend fun approve(token:String):StickerReply=withContext(Dispatchers.IO) {lock.withLock {
        try {retire();val plan=pending[token] ?: return@withLock repository.receipt("token/$token")?.let {completed(it.first.substringBefore('\n'),token)} ?: StickerReply.Failure(StickerError.NOT_FOUND,"预览已失效")
            plan.approved=true;commit(plan.owner,token)}
        catch(error:StickerFault) {StickerReply.Failure(error.code,error.message.orEmpty(),repository.snapshot.value.revision)}
    }}
    suspend fun reject(token:String)=withContext(Dispatchers.IO) {lock.withLock {pending[token]?.let(::discard)}}
    companion object {
        val operations=listOf("query","query-frames","changes","prepare","commit","discard","transaction","publish-image","create-qr-code","import-media","read-image","publish-quick-media","export-card","open-ui")
        private fun key(owner:String,id:String)="$owner/$id"
    }
}
