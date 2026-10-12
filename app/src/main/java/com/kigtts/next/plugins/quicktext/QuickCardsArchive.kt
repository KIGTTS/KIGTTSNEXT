package com.kigtts.next.plugins.quicktext

import com.kigtts.plugin.api.*
import com.kigtts.next.media.*
import kotlinx.coroutines.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Portable bundles stage all resources under the importing/exporting plugin's own data root. */
internal class QuickCardsArchive(private val host:QuickTextHost,private val owner:String,private val caps:Set<String>) {
    fun export(command:QuickTextCommand.Export,snapshot:QuickTextSnapshot):QuickTextReply.Resource {
        requireQuick(command.groupIds.all {id->snapshot.groups.any {it.id==id}}&&command.entryIds.all {id->snapshot.entries.any {it.id==id}},QuickTextError.NOT_FOUND,"选中的条目或分组不存在")
        val entries=snapshot.entries.filter {command.groupIds.isEmpty()&&command.entryIds.isEmpty()||it.id in command.entryIds||it.groupId in command.groupIds}
        val groups=snapshot.groups.filter {command.groupIds.isEmpty()&&command.entryIds.isEmpty()||it.id in command.groupIds||entries.any {entry->entry.groupId==it.id}}
        val resources=mutableListOf<Pair<String,File>>()
        val metadata=if(!command.includeMedia)emptyList() else entries.mapNotNull {it.mediaId}.distinct().map {id->
            val entry=host.media.get(id) ?: throw QuickTextFault(QuickTextError.NOT_FOUND,"附件已被移除")
            fun add(file:File?,path:String):String {val source=file ?: throw QuickTextFault(QuickTextError.NOT_FOUND,"附件资源不存在");resources+=path to source;return path}
            val prefix="media/$id"
            val root=if(entry.kind==MediaKind.HTML)QuickTextResources(host,owner,caps).root(entry) else null
            val primary=when(entry.kind) {
                MediaKind.WEB->entry.primary
                MediaKind.HTML->{val base=requireNotNull(root).canonicalFile
                    base.walkTopDown().filter {it.isFile&&!it.name.startsWith(".kigtts-")}.forEach {file->
                        requireQuick(file.canonicalFile.toPath().startsWith(base.toPath()),message="页面包含外部链接")
                        add(file,"$prefix/root/"+file.relativeTo(base).invariantSeparatorsPath)
                        requireQuick(resources.size<=2048,message="资源数量过多")
                    };"$prefix/root/"+requireNotNull(host.media.file(entry)).relativeTo(base).invariantSeparatorsPath
                }
                else->add(host.media.file(entry),"$prefix/source."+requireNotNull(host.media.file(entry)).extension)
            }
            val video=entry.video?.let {add(host.media.file(entry,true),"$prefix/motion.mp4")}
            QuickArchiveMedia(id,entry.kind.name,primary,entry.mimeType,video,root?.let {"$prefix/root"},entry.network,entry.appleLive,entry.scale.name,
                entry.thumbnailMode.name,entry.focusX,entry.focusY)
        }
        requireQuick(resources.size<=2048&&resources.sumOf {it.second.length()}<=MAX_BYTES,message="导出资源过多或过大")
        val bundle=QuickCardsBundle(groups=groups,entries=entries.map {if(command.includeMedia)it else it.copy(kind=QuickCardKind.TEXT,mediaId=null)},media=metadata)
        val json=PluginJson.encodeToString(bundle).toByteArray(Charsets.UTF_8);requireQuick(json.size<=MAX_JSON,message="条目数据过大，请分批导出")
        val target=host.paths.storage(owner).file(command.destination);target.parentFile!!.mkdirs()
        val stage=File(target.parentFile,".kigtts-export-${UUID.randomUUID()}")
        try {ZipOutputStream(stage.outputStream()).use {zip->
            zip.putNextEntry(ZipEntry("cards.json"));zip.write(json);zip.closeEntry()
            var bytes=0L
            resources.forEach {(path,file)->requireQuick(safe(path),message="资源路径不合法");zip.putNextEntry(ZipEntry(path))
                file.inputStream().use {input->val buffer=ByteArray(64*1024);while(true) {val n=input.read(buffer);if(n<0)break;bytes+=n;requireQuick(bytes<=MAX_BYTES,message="资源过大");zip.write(buffer,0,n)}};zip.closeEntry()
            }
        };Files.move(stage.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING)} finally {stage.delete()}
        return QuickTextReply.Resource(PluginResource(command.destination,owner),target.length())
    }
    suspend fun import(command:QuickTextCommand.Import):QuickTextReply {
        requireQuick(command.source.pluginId==null||command.source.pluginId==owner,QuickTextError.PERMISSION_DENIED,"只能导入本插件提供的资源")
        val source=host.paths.storage(owner).file(command.source.path)
        val hash=withContext(Dispatchers.IO) {java.security.MessageDigest.getInstance("SHA-256").let {digest->source.inputStream().use {input->val buffer=ByteArray(64*1024);while(true) {val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)}};digest.digest().joinToString("") {"%02x".format(it)}}}
        val fingerprint=QuickTextHost.fingerprint(command)+hash
        host.existing(owner,command.transactionId,fingerprint)?.let {return it}
        requireQuick(command.revision==host.library.revision.value,QuickTextError.CONFLICT,"快捷文本已更新，请重新读取")
        val directory="resources/quick-imports/"+UUID.randomUUID()
        val stage=host.paths.storage(owner).directory(directory);val created=mutableListOf<String>()
        try {
            val bundle=withContext(Dispatchers.IO) {read(host.paths.storage(owner).file(command.source.path),stage)}
            requireQuick(bundle.schema==1&&bundle.groups.size<=256&&bundle.entries.size<=10_000&&bundle.media.size<=2048,message="导入格式或数量不合法")
            requireQuick(bundle.groups.map {it.id}.distinct().size==bundle.groups.size&&bundle.entries.map {it.id}.distinct().size==bundle.entries.size&&
                bundle.media.map {it.id}.distinct().size==bundle.media.size,message="包内标识重复")
            val mediaIds=withContext(Dispatchers.IO) {bundle.media.associate {item->
                val kind=MediaKind.valueOf(item.kind);requireQuick(kind!=MediaKind.WEB||WebContentPolicy.https(item.primary),message="网页地址需要 HTTPS")
                requireQuick(!(item.network||kind==MediaKind.WEB)||"network" in caps,QuickTextError.PERMISSION_DENIED,"导入页面需要 network 能力")
                fun path(value:String):String {requireQuick(safe(value)&&File(stage,value).isFile,message="资源缺失或路径不合法");return "$directory/$value"}
                item.root?.let {requireQuick(safe(it)&&File(stage,it).isDirectory,message="页面目录不存在")}
                val entry=MediaEntry(UUID.randomUUID().toString(),kind.label,kind,if(kind==MediaKind.WEB)item.primary else path(item.primary),item.mime,
                    item.video?.let(::path),owner,network=item.network,appleLive=item.appleLive,scale=MediaScale.valueOf(item.scale),
                    thumbnailMode=ThumbnailMode.valueOf(item.thumbnailMode),focusX=item.focusX,focusY=item.focusY,projectRoot=item.root?.let {"$directory/$it"})
                if(kind==MediaKind.HTML)requireQuick(File(stage,item.primary).length()<=HtmlProjectFiles.TEXT_LIMIT&&item.root!=null&&item.primary.startsWith(item.root+"/"),message="HTML 主页面不合法")
                val saved=if(kind==MediaKind.WEB)host.media.web("网页",item.primary,owner) else host.media.registerPluginMedia(entry)
                created+=saved.id;item.id to saved.id
            } }
            val existing=host.library.groups().map {it.name}.toMutableSet()
            val groups=bundle.groups.map {group->var name=group.name;var index=2;while(name in existing)name=group.name+" (${index++})";existing+=name
                group.copy(id=UUID.randomUUID().toString(),name=name)}
            val ids=bundle.groups.zip(groups).associate {it.first.id to it.second.id}
            val edits=groups.map {QuickTextEdit.PutGroup(it)}+bundle.entries.map {entry->QuickTextEdit.PutEntry(entry.copy(id=UUID.randomUUID().toString(),
                groupId=ids[entry.groupId] ?: throw QuickTextFault(QuickTextError.INVALID,"条目分组不存在"),
                mediaId=entry.mediaId?.let {mediaIds[it] ?: throw QuickTextFault(QuickTextError.INVALID,"条目附件不存在")}))}
            val cleanup={created.forEach(host.media::remove);stage.deleteRecursively();Unit}
            requireQuick(edits.isNotEmpty(),message="导入包为空")
            val reply=host.prepare(owner,QuickTextCommand.Prepare(command.transactionId,command.revision,edits),cleanup,maxOperations=10256,fingerprintOverride=fingerprint)
            if(reply is QuickTextReply.Prepared) {
                val json=org.json.JSONObject().put("groups",org.json.JSONObject(ids)).put("media",org.json.JSONObject(mediaIds))
                    .put("entries",org.json.JSONObject(bundle.entries.zip(edits.filterIsInstance<QuickTextEdit.PutEntry>()).associate {it.first.id to it.second.entry.id}))
                withContext(Dispatchers.IO) {File(stage,"mapping.json").writeText(json.toString(),Charsets.UTF_8)}
                val resource=PluginResource("$directory/mapping.json",owner);host.mapping(reply.value.token,resource)
                return reply.copy(mapping=resource)
            }
            if(reply !is QuickTextReply.Prepared)cleanup()
            return reply
        } catch(e:Exception) {withContext(NonCancellable+Dispatchers.IO) {created.forEach(host.media::remove);stage.deleteRecursively()};throw e}
    }
    private fun read(source:File,stage:File):QuickCardsBundle=ZipFile(source).use {zip->
        requireQuick(source.length()<=MAX_BYTES,message="导入包过大")
        val entries=zip.entries().toList();requireQuick(entries.size<=2049&&entries.map {it.name}.distinct().size==entries.size&&entries.all {safe(it.name.removeSuffix("/"))},message="导入包路径或数量不合法")
        val metadata=zip.getEntry("cards.json") ?: throw QuickTextFault(QuickTextError.INVALID,"缺少 cards.json")
        var total=0L
        entries.filterNot {it.isDirectory}.forEach {entry->
            val target=File(stage,entry.name).canonicalFile;requireQuick(target.toPath().startsWith(stage.canonicalFile.toPath()),message="文件超出目录")
            target.parentFile!!.mkdirs();var bytes=0L
            zip.getInputStream(entry).use {input->target.outputStream().use {output->val buffer=ByteArray(64*1024)
                while(true) {val n=input.read(buffer);if(n<0)break;bytes+=n;total+=n
                    requireQuick(total<=MAX_BYTES&&(entry.name!=metadata.name||bytes<=MAX_JSON),message="导入包展开后过大");output.write(buffer,0,n)}
            }}
        }
        PluginJson.decodeFromString(File(stage,"cards.json").readText(Charsets.UTF_8))
    }
    companion object {
        const val MAX_BYTES=2L*1024*1024*1024;const val MAX_JSON=8*1024*1024
        fun safe(path:String)=path.isNotEmpty()&&path.length<=1024&&!path.startsWith('/')&&path.split('/').all(HtmlProjectFiles::validName)
    }
}
