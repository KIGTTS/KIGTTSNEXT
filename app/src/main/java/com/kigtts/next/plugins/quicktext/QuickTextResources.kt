package com.kigtts.next.plugins.quicktext

import com.kigtts.plugin.api.*
import com.kigtts.next.media.*
import kotlinx.coroutines.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

internal class QuickTextResources(private val host:QuickTextHost,private val owner:String,private val capabilities:Set<String>) {
    private fun source(ref:PluginResource):File {
        requireQuick(ref.pluginId==null||ref.pluginId==owner,QuickTextError.PERMISSION_DENIED,"只能使用本插件提供的资源")
        return host.paths.storage(owner).file(ref.path)
    }
    private fun entry(id:String)=host.media.get(id) ?: throw QuickTextFault(QuickTextError.NOT_FOUND,"附件不存在")
    internal fun root(entry:MediaEntry):File {
        requireQuick(entry.kind==MediaKind.HTML,message="此附件不是本地页面")
        return if(entry.pluginId==null)host.media.htmlFiles.root(entry.id)
        else entry.projectRoot?.let {host.paths.storage(entry.pluginId).file(it)} ?: requireNotNull(host.media.file(entry)?.parentFile)
    }
    private fun file(entry:MediaEntry,path:String):File {
        requireQuick(path.isEmpty()||path.split('/').all(HtmlProjectFiles::validName),message="页面路径不合法")
        val root=root(entry).canonicalFile
        return File(root,path).canonicalFile.also {requireQuick(it.toPath().startsWith(root.toPath()),message="文件超出页面目录")}
    }
    private fun writable(entry:MediaEntry,version:Long) {
        requireQuick(entry.pluginId==null||entry.pluginId==owner,QuickTextError.PERMISSION_DENIED,"不能改写其它插件的资源")
        requireQuick(entry.created==version,QuickTextError.CONFLICT,"页面文件已更新，请重新读取")
    }
    private fun network(allowed:Boolean) {requireQuick(!allowed||"network" in capabilities,QuickTextError.PERMISSION_DENIED,"此页面需要插件声明 network 能力")}
    suspend fun publish(command:QuickTextCommand.PublishPage):QuickTextReply.Media=withContext(Dispatchers.IO) {
        network(command.network)
        val root=source(command.root).canonicalFile;requireQuick(root.isDirectory,message="页面项目目录不存在")
        requireQuick(command.mainPage.split('/').all(HtmlProjectFiles::validName)&&command.mainPage.substringAfterLast('.').lowercase() in setOf("html","htm"),message="主页面路径不合法")
        val main=File(root,command.mainPage).canonicalFile
        requireQuick(main.toPath().startsWith(root.toPath())&&main.isFile&&main.length()<=HtmlProjectFiles.TEXT_LIMIT,message="主页面不存在或过大")
        requireQuick(command.allowedActions.size<=32&&command.allowedActions.all {it.length in 1..256},message="网页操作标识不合法")
        command.mediaId?.let {writable(entry(it),requireNotNull(command.version));requireQuick(entry(it).pluginId==owner&&entry(it).kind==MediaKind.HTML,message="只能更新本插件发布的页面")}
        val id=command.mediaId ?: UUID.randomUUID().toString()
        val value=host.media.registerPluginMedia(MediaEntry(id,command.name.take(256),MediaKind.HTML,
            command.root.path+"/"+command.mainPage,"text/html",pluginId=owner,actions=command.allowedActions,network=command.network,projectRoot=command.root.path))
        QuickTextReply.Media(value.dto())
    }
    suspend fun publish(command:QuickTextCommand.PublishMedia):QuickTextReply.Media=withContext(Dispatchers.IO) {
        requireQuick(command.content !is SubtitleContent.Text,message="文本请通过条目批量接口保存")
        network(when(val content=command.content) {is SubtitleContent.Web->true;is SubtitleContent.Html->content.allowNetwork;else->false})
        QuickTextReply.Media(host.media.plugin(owner,command.content).dto())
    }
    suspend fun audio(command:QuickTextCommand.PublishAudio):QuickTextReply.Media=withContext(Dispatchers.IO) {
        val file=source(command.source);requireQuick(file.isFile&&file.length() in 1..MAX_BYTES,message="音频资源不存在或过大")
        val mime=android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()).orEmpty()
        requireQuick(mime.startsWith("audio/"),message="请选择音频资源")
        QuickTextReply.Media(host.media.registerPluginMedia(MediaEntry(UUID.randomUUID().toString(),command.name.take(256),MediaKind.AUDIO,
            command.source.path,mime,pluginId=owner)).dto())
    }
    suspend fun list(command:QuickTextCommand.Files):QuickTextReply.Files=withContext(Dispatchers.IO) {
        requireQuick(command.offset>=0&&command.limit in 1..100,message="分页参数不合法")
        val entry=entry(command.mediaId);val directory=file(entry,command.directory)
        requireQuick(directory.isDirectory,message="文件夹不存在")
        val all=directory.listFiles().orEmpty().filterNot {it.name.startsWith(".kigtts-")}.sortedWith(compareBy<File> {!it.isDirectory}.thenBy {it.name})
        val main=host.media.file(entry)?.canonicalFile
        val rows=all.drop(command.offset).take(command.limit).map {QuickFileInfo(it.relativeTo(root(entry)).invariantSeparatorsPath,it.isDirectory,it.length(),it.canonicalFile==main)}
        QuickTextReply.Files(entry.created,rows,all.size,(command.offset+rows.size).takeIf {it<all.size})
    }
    suspend fun get(command:QuickTextCommand.GetFile):QuickTextReply.Resource=withContext(Dispatchers.IO) {
        val file=file(entry(command.mediaId),command.path);requireQuick(file.isFile&&file.length()<=MAX_BYTES,message="文件不存在或过大")
        val target=host.paths.storage(owner).file(command.destination);copy(file,target)
        QuickTextReply.Resource(PluginResource(command.destination,owner),target.length())
    }
    suspend fun put(command:QuickTextCommand.PutFile):QuickTextReply.Media=withContext(Dispatchers.IO) {
        host.checkPageDraft(command.mediaId)
        val entry=entry(command.mediaId);writable(entry,command.version)
        val source=source(command.source);requireQuick(source.isFile&&source.length()<=MAX_BYTES,message="输入文件不存在或过大")
        val destination=file(entry,command.path);requireQuick(command.path.isNotEmpty(),message="不能覆盖项目根目录")
        if(destination==host.media.file(entry))requireQuick(source.length()<=HtmlProjectFiles.TEXT_LIMIT,message="主页面内容过大")
        copy(source,destination);QuickTextReply.Media(host.media.touchMedia(entry.id).dto())
    }
    suspend fun main(command:QuickTextCommand.SetMain):QuickTextReply.Media=withContext(Dispatchers.IO) {
        host.checkPageDraft(command.mediaId)
        val entry=entry(command.mediaId);writable(entry,command.version)
        val file=file(entry,command.path);requireQuick(file.isFile&&file.length()<=HtmlProjectFiles.TEXT_LIMIT&&file.extension.lowercase() in setOf("html","htm"),message="请选择有效 HTML 主页面")
        val updated=if(entry.pluginId==null) {host.media.htmlFiles.selectMain(entry.id,command.path);requireNotNull(host.media.get(entry.id))}
            else host.media.registerPluginMedia(entry.copy(primary=file.relativeTo(host.paths.storage(owner).root).invariantSeparatorsPath))
        QuickTextReply.Media(updated.dto())
    }
    suspend fun presentation(command:QuickTextCommand.Presentation):QuickTextReply.Media=withContext(Dispatchers.IO) {
        val entry=entry(command.mediaId);requireQuick(entry.created==command.version,QuickTextError.CONFLICT,"媒体设置已更新")
        val value=entry.copy(scale=command.scale?.let(MediaScale::valueOf) ?: entry.scale,
            thumbnailMode=command.thumbnailMode?.let(ThumbnailMode::valueOf) ?: entry.thumbnailMode,
            focusX=command.focusX ?: entry.focusX,focusY=command.focusY ?: entry.focusY)
        QuickTextReply.Media(host.media.presentation(value).dto())
    }
    suspend fun edit(command:QuickTextCommand.EditFiles):QuickTextReply.Media=withContext(Dispatchers.IO) {
        host.checkPageDraft(command.mediaId)
        val entry=entry(command.mediaId);writable(entry,command.version)
        val root=root(entry).canonicalFile;val parent=requireNotNull(root.parentFile)
        val stage=File(parent,".kigtts-project-${UUID.randomUUID()}");val backup=File(parent,".kigtts-backup-${UUID.randomUUID()}")
        var main=requireNotNull(host.media.file(entry)).canonicalFile.relativeTo(root).invariantSeparatorsPath
        fun staged(path:String):File {requireQuick(path.isNotEmpty()&&path.split('/').all(HtmlProjectFiles::validName),message="文件路径不合法")
            return File(stage,path).canonicalFile.also {requireQuick(it.toPath().startsWith(stage.canonicalFile.toPath()),message="文件超出项目目录")}}
        requireQuick(command.edits.size in 1..100,message="文件批次大小不合法")
        try {
            HtmlProjectFiles.copyDirectory(root,stage)
            command.edits.forEach {operation->when(operation) {
                is QuickFileEdit.Directory->requireQuick(staged(operation.path).mkdirs(),message="文件夹已存在或无法创建")
                is QuickFileEdit.Put->copy(source(operation.source),staged(operation.path))
                is QuickFileEdit.Move->{val from=staged(operation.path);val to=staged(operation.destination)
                    requireQuick(from.exists()&&!to.exists()&&!to.toPath().startsWith(from.toPath()),message="移动目标不合法")
                    to.parentFile!!.mkdirs();Files.move(from.toPath(),to.toPath())
                    if(main==operation.path||main.startsWith(operation.path+"/"))main=operation.destination+main.removePrefix(operation.path)
                }
                is QuickFileEdit.Remove->operation.paths.forEach {path->requireQuick(main!=path&&!main.startsWith("$path/"),message="请先指定新的主页面")
                    requireQuick(staged(path).deleteRecursively(),message="文件无法删除")}
                is QuickFileEdit.Main->main=operation.path
            } }
            val final=staged(main);requireQuick(final.isFile&&final.extension.lowercase() in setOf("html","htm")&&final.length()<=HtmlProjectFiles.TEXT_LIMIT,message="主页面不存在或过大")
            writable(entry(command.mediaId),command.version)
            Files.move(root.toPath(),backup.toPath())
            try {
                Files.move(stage.toPath(),root.toPath())
                val updated=if(entry.pluginId==null) {host.media.htmlFiles.selectMain(entry.id,main);requireNotNull(host.media.get(entry.id))}
                    else host.media.registerPluginMedia(entry.copy(primary=File(root,main).relativeTo(host.paths.storage(owner).root).invariantSeparatorsPath))
                backup.deleteRecursively();QuickTextReply.Media(updated.dto())
            } catch(e:Exception) {root.deleteRecursively();Files.move(backup.toPath(),root.toPath());throw e}
        } finally {stage.deleteRecursively()}
    }
    companion object {
        const val MAX_BYTES=2L*1024*1024*1024
        internal fun copy(source:File,target:File) {
            requireQuick(source.isFile&&source.length()<=MAX_BYTES,message="资源不存在或过大")
            target.parentFile!!.mkdirs();val stage=File(target.parentFile,".kigtts-copy-${UUID.randomUUID()}")
            try {source.inputStream().use {input->stage.outputStream().use {output->var total=0L;val buffer=ByteArray(64*1024)
                while(true) {val count=input.read(buffer);if(count<0)break;total+=count;requireQuick(total<=MAX_BYTES,message="资源过大");output.write(buffer,0,count)} }}
                Files.move(stage.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING)
            } finally {stage.delete()}
        }
    }
}
