package com.kigtts.next.plugins.quicktext

import com.kigtts.next.state.*
import com.kigtts.next.media.*
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PluginPaths
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject

/** One library owner serves foreground UI and background plugin requests on the main dispatcher. */
class QuickTextHost(val library:QuickTextLibrary,val media:MediaRepository,val paths:PluginPaths,private val ui:()->AppViewModel?) {
    private val pending=linkedMapOf<String,QuickPlan>()
    private val operationsLock=kotlinx.coroutines.sync.Mutex()
    private val decisions=MutableStateFlow(0L)
    private val cleanupScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    fun forPlugin(owner:String,capabilities:Set<String>):QuickTextBroker {
        paths.validateId(owner)
        return object:QuickTextBroker {
            override suspend fun request(command:QuickTextCommand):QuickTextReply=withContext(Dispatchers.Main) {operationsLock.lock()
                try {
                    val write=command !is QuickTextCommand.Query&&command !is QuickTextCommand.Describe&&command !is QuickTextCommand.Transaction&&
                        command !is QuickTextCommand.Files&&command !is QuickTextCommand.GetFile&&command !is QuickTextCommand.Export
                    requireQuick("quick-text-write" in capabilities || !write&&"quick-text-read" in capabilities,QuickTextError.PERMISSION_DENIED,"插件未声明所需的快捷文本能力")
                    retire()
                    when(command) {
                        QuickTextCommand.Describe->QuickTextReply.Features(QuickTextFeatures(operations=operations,writable="quick-text-write" in capabilities,groupIcons=CategoryIcon.entries.map {it.name}))
                        is QuickTextCommand.Query->query(command)
                        is QuickTextCommand.Prepare->prepare(owner,command)
                        is QuickTextCommand.Commit->commit(owner,command.token)
                        is QuickTextCommand.Discard->{requireQuick(pending[command.token]?.owner==owner,QuickTextError.PERMISSION_DENIED,"预览不属于本插件");reject(command.token);QuickTextReply.Discarded(command.token)}
                        is QuickTextCommand.Transaction->receipt(owner,command.transactionId)?.second ?: pending.values.firstOrNull {it.owner==owner&&it.transactionId==command.transactionId}
                            ?.let {QuickTextReply.Prepared(it.preview,it.mapping)} ?: QuickTextReply.Failure(QuickTextError.NOT_FOUND,"没有此事务")
                        is QuickTextCommand.PublishPage->QuickTextResources(this@QuickTextHost,owner,capabilities).publish(command)
                        is QuickTextCommand.PublishMedia->QuickTextResources(this@QuickTextHost,owner,capabilities).publish(command)
                        is QuickTextCommand.PublishAudio->QuickTextResources(this@QuickTextHost,owner,capabilities).audio(command)
                        is QuickTextCommand.Files->QuickTextResources(this@QuickTextHost,owner,capabilities).list(command)
                        is QuickTextCommand.GetFile->QuickTextResources(this@QuickTextHost,owner,capabilities).get(command)
                        is QuickTextCommand.PutFile->QuickTextResources(this@QuickTextHost,owner,capabilities).put(command)
                        is QuickTextCommand.SetMain->QuickTextResources(this@QuickTextHost,owner,capabilities).main(command)
                        is QuickTextCommand.Presentation->QuickTextResources(this@QuickTextHost,owner,capabilities).presentation(command)
                        is QuickTextCommand.EditFiles->QuickTextResources(this@QuickTextHost,owner,capabilities).edit(command)
                        is QuickTextCommand.Export->{val snapshot=snapshot();withContext(Dispatchers.IO) { QuickCardsArchive(this@QuickTextHost,owner,capabilities).export(command,snapshot) }}
                        is QuickTextCommand.Import->QuickCardsArchive(this@QuickTextHost,owner,capabilities).import(command)
                        is QuickTextCommand.OpenUi->open(owner,command)
                    }
                } catch(cancel:CancellationException) {throw cancel}
                catch(e:QuickTextFault) {QuickTextReply.Failure(e.code,e.message.orEmpty(),library.revision.value)}
                catch(e:Exception) {QuickTextReply.Failure(QuickTextError.INVALID,e.message ?: "操作失败",library.revision.value)}
                finally {operationsLock.unlock()}
            }
            override fun changes(afterRevision:Long):Flow<QuickTextChange> {
                requireQuick(capabilities.any {it in setOf("quick-text-read","quick-text-write")},QuickTextError.PERMISSION_DENIED,"插件未声明快捷文本能力")
                return combine(library.revision,media.entries,decisions) {version,entries,decision->QuickTextChange(version,
                    java.security.MessageDigest.getInstance("SHA-256").digest(entries.joinToString("|") {it.id+":"+it.created+":"+it.scale+":"+it.thumbnailMode}.toByteArray())
                        .joinToString("") {"%02x".format(it)},decisionsVersion=decision) }.distinctUntilChanged()
            }
        }
    }
    private fun query(command:QuickTextCommand.Query):QuickTextReply.Snapshot {
        requireQuick(command.revision==null||command.revision==library.revision.value,QuickTextError.CONFLICT,"快捷文本已更新，请重新读取")
        requireQuick(command.offset>=0&&command.limit in 0..50,message="分页参数不合法")
        val groups=library.groups()
        requireQuick(command.groupId==null||groups.any {it.id==command.groupId},QuickTextError.NOT_FOUND,"分组不存在")
        val name=groups.firstOrNull {it.id==command.groupId}?.name
        val all=library.phrases.value.filter {command.groupId==null||it.category==name}
        val entries=all.drop(command.offset).take(command.limit).map {it.dto(groups,media.get(it.mediaId))}.map {if(command.includeContent)it else it.copy(text="",formats=emptyList())}
        val attachments=entries.mapNotNull {media.get(it.mediaId)?.dto()}.distinctBy {it.id}
        return QuickTextReply.Snapshot(QuickTextSnapshot(library.revision.value,groups,entries,command.offset,all.size,
            (command.offset+entries.size).takeIf {entries.isNotEmpty()&&it<all.size},attachments,
            groups.firstOrNull {it.name==ui()?.state?.value?.selectedCategory}?.id,ui()?.content?.phraseDraft?.value?.phraseId))
    }
    internal fun snapshot():QuickTextSnapshot {val groups=library.groups();return QuickTextSnapshot(library.revision.value,groups,
        library.phrases.value.map {it.dto(groups,media.get(it.mediaId))},media=media.entries.value.map {it.dto()})}
    internal fun existing(owner:String,id:String,fingerprint:String):QuickTextReply? {
        receipt(owner,id)?.let { requireQuick(it.first==fingerprint,QuickTextError.CONFLICT,"事务标识已用于其它修改");return it.second }
        pending.values.firstOrNull {it.owner==owner&&it.transactionId==id}?.let {
            requireQuick(it.fingerprint==fingerprint,QuickTextError.CONFLICT,"事务标识已用于其它修改");return QuickTextReply.Prepared(it.preview,it.mapping)
        };return null
    }
    internal suspend fun prepare(owner:String,command:QuickTextCommand.Prepare,cleanup:()->Unit={},maxOperations:Int=100,fingerprintOverride:String?=null):QuickTextReply {
        val fingerprint=fingerprintOverride ?: fingerprint(command)
        existing(owner,command.transactionId,fingerprint)?.let {return it}
        requireQuick(pending.size<16&&pending.values.count {it.owner==owner}<4,QuickTextError.BUSY,"请先完成已有的预览")
        val groups=library.groups();val items=library.phrases.value;val catalog=media.entries.value.associate {it.id to it.kind};val revision=library.revision.value
        val plan=withContext(Dispatchers.Default) {planBatch(owner,command,groups,items.map {it.dto(groups,media.get(it.mediaId))},revision,catalog,maxOperations)}
            .copy(fingerprint=fingerprint,cleanup=cleanup)
        requireQuick(library.revision.value==revision,QuickTextError.CONFLICT,"快捷文本已更新，请重新读取")
        checkDraft(plan)
        pending[plan.preview.token]=plan;return QuickTextReply.Prepared(plan.preview)
    }
    internal fun plan(token:String):QuickBatchPreview?=pending[token]?.takeIf {it.expires>System.currentTimeMillis()}?.preview
    private fun checkDraft(plan:QuickPlan) {
        val model=ui() ?: return;val draft=model.content.phraseDraft.value ?: return
        val original=library.phrases.value.firstOrNull {it.id==draft.phraseId}
        val replacement=plan.entries.firstOrNull {it.id==draft.phraseId}
        val groupChanged=library.groups().firstOrNull {it.name==draft.category}?.let {old->plan.groups.firstOrNull {it.id==old.id}!=old} ?: false
        requireQuick(original?.dto(library.groups(),media.get(original.mediaId))==replacement&&!groupChanged,QuickTextError.BUSY,"该条目或分组正在编辑，请稍后重试")
    }
    private fun commit(owner:String,token:String):QuickTextReply {
        val plan=pending[token] ?: throw QuickTextFault(QuickTextError.NOT_FOUND,"预览已失效，请重新准备")
        requireQuick(plan.owner==owner,QuickTextError.PERMISSION_DENIED,"预览属于其它插件")
        requireQuick(library.revision.value==plan.preview.revision,QuickTextError.CONFLICT,"快捷文本已更新，请重新预览")
        checkDraft(plan)
        if(plan.preview.requiresConfirmation&&!plan.approved)return QuickTextReply.Failure(QuickTextError.CONFIRMATION_REQUIRED,"请打开修改预览并确认删除",reviewToken=token)
        plan.entries.forEach {requireQuick(it.mediaId==null||media.get(it.mediaId)!=null,QuickTextError.NOT_FOUND,"附件已被移除")}
        val reply=QuickTextReply.Applied(library.revision.value+1,plan.transactionId,plan.mapping)
        val selected=ui()?.state?.value?.selectedCategory?.let {name->library.groups().firstOrNull {it.name==name}?.id}
        library.commit(plan.entries.map {it.native(plan.groups)},plan.groups.map {it.name},plan.groups.associate {it.name to CategoryIcon.valueOf(it.icon)},
            plan.groups.associate {it.name to it.id},plan.preview.revision,receiptKey(owner,plan.transactionId) to JSONObject().put("fingerprint",plan.fingerprint)
                .put("reply",PluginJson.encodeToString<QuickTextReply>(reply)).toString())
        ui()?.category(plan.groups.firstOrNull {it.id==selected}?.name ?: plan.groups.first().name)
        pending.remove(token);return reply
    }
    suspend fun approve(token:String):QuickTextReply=withContext(Dispatchers.Main) {
        val plan=pending[token] ?: return@withContext QuickTextReply.Failure(QuickTextError.NOT_FOUND,"预览已失效")
        plan.approved=true;forPlugin(plan.owner,setOf("quick-text-write")).request(QuickTextCommand.Commit(token))
    }
    fun reject(token:String) {pending.remove(token)?.let {plan->
        library.receipt(receiptKey(plan.owner,plan.transactionId),JSONObject().put("fingerprint",plan.fingerprint)
            .put("reply",PluginJson.encodeToString<QuickTextReply>(QuickTextReply.Discarded(token))).toString())
        decisions.value++;cleanupScope.launch {runCatching {plan.cleanup()}}
    }}
    internal fun mapping(token:String,resource:PluginResource) {pending[token]?.mapping=resource}
    internal fun checkPageDraft(id:String) {requireQuick(ui()?.content?.editorDraft?.value?.mediaId!=id,QuickTextError.BUSY,"页面正在编辑，请稍后重试")}
    private suspend fun open(owner:String,command:QuickTextCommand.OpenUi):QuickTextReply {
        val model=ui() ?: throw QuickTextFault(QuickTextError.HOST_UNAVAILABLE,"请先打开 KIGTTS")
        if(command.target !in setOf(QuickUiTarget.REVIEW,QuickUiTarget.PAGE_PREVIEW))requireQuick(model.content.phraseDraft.value==null&&model.content.editorDraft.value==null,
            QuickTextError.BUSY,"正在编辑内容，请先完成或取消编辑")
        when(command.target) {
            QuickUiTarget.MANAGE-> {model.navigate(Destination.Home);model.editQuickTexts()}
            QuickUiTarget.NEW_ENTRY->{val group=library.groups().firstOrNull {it.id==command.id} ?: library.groups().first();model.editPhraseDetails(null,group.name)}
            QuickUiTarget.EDIT_ENTRY->{val item=library.phrases.value.firstOrNull {it.id==command.id} ?: throw QuickTextFault(QuickTextError.NOT_FOUND,"条目不存在");model.editPhraseDetails(item,item.category)}
            QuickUiTarget.PAGE_FILES->model.content.htmlFiles(selectedMedia=requireNotNull(command.id))
            QuickUiTarget.PAGE_PREVIEW->{requireQuick(media.get(command.id)?.kind in setOf(MediaKind.HTML,MediaKind.WEB),QuickTextError.NOT_FOUND,"页面不存在");model.navigate(Destination.HtmlPreview(requireNotNull(command.id)))}
            QuickUiTarget.REVIEW->{val plan=pending[command.id] ?: throw QuickTextFault(QuickTextError.NOT_FOUND,"预览已失效")
                requireQuick(plan.owner==owner,QuickTextError.PERMISSION_DENIED,"预览属于其它插件");model.navigate(Destination.QuickTextReview(plan.preview.token))}
        }
        return QuickTextReply.UiOpened(command.target)
    }
    private fun retire() {pending.values.filter {it.expires<=System.currentTimeMillis()}.map {it.preview.token}.forEach(::reject)}
    private fun receipt(owner:String,id:String):Pair<String,QuickTextReply>?=library.receipt(receiptKey(owner,id))?.let {raw->
        val json=JSONObject(raw);json.getString("fingerprint") to PluginJson.decodeFromString(json.getString("reply"))}
    companion object {
        val operations=listOf("query","prepare","commit","discard","transaction","changes","publish-page","publish-media","publish-audio","files","get-file","put-file","set-main","presentation","edit-files","export","import","open-ui")
        private fun receiptKey(owner:String,id:String)="$owner/$id"
        internal fun fingerprint(command:QuickTextCommand)=java.security.MessageDigest.getInstance("SHA-256").digest(PluginJson.encodeToString(command).toByteArray())
            .joinToString("") {"%02x".format(it)}
    }
}
