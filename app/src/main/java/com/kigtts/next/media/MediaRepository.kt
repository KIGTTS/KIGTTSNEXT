package com.kigtts.next.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.kigtts.next.i18n.uiCopy
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PluginPaths
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.io.File
import java.util.UUID

/** Host-owned imports are durable copies; plugin media stays referenced in its owner's Appdata. */
class MediaRepository(private val context: Context, private val plugins: PluginPaths) {
    val root=File(requireNotNull(context.getExternalFilesDir(null)),"appdata/Kigtts/media").apply { mkdirs() }
    private val catalog=File(root,"catalog.json")
    private val state=MutableStateFlow(runCatching { val array=JSONArray(catalog.readText()); (0 until array.length()).map { MediaEntry.read(array.getJSONObject(it)) } }.getOrDefault(emptyList()))
    val entries=state.asStateFlow()
    val thumbnails = com.kigtts.next.media.thumbnail.MediaThumbnails(this)
    val htmlFiles = HtmlProjectFiles(this)
    val htmlImports = HtmlProjectImports(context, this)
    val icons = UiIcons(context, File(root, "ui-icons"))
    internal fun updateHtml(id: String, primary: String): MediaEntry {
        val entry = requireNotNull(get(id));require(entry.kind == MediaKind.HTML && entry.pluginId == null)
        require(scoped(primary).isFile)
        return save(entry.copy(primary=primary,created=maxOf(System.currentTimeMillis(),entry.created+1)))
    }
    fun editableHtml(id: String?): MediaEntry {
        val entry = get(id)
        if(entry?.kind == MediaKind.HTML && entry.pluginId == null)return entry
        val created = html("新页面",HtmlProjectFiles.TEMPLATE)
        if(entry?.kind == MediaKind.HTML) {
            val source = file(entry) ?: return created
            val target = htmlFiles.root(created.id)
            val copy = File(root,".kigtts-copy-${created.id}")
            try { HtmlProjectFiles.copyDirectory(requireNotNull(source.parentFile),copy)
                copy.listFiles().orEmpty().forEach { f -> f.copyRecursively(File(target,f.name),overwrite=true) }
                htmlFiles.selectMain(created.id,source.name)
            } catch(e:Exception) { remove(created.id);throw e } finally { copy.deleteRecursively() }
        }
        return requireNotNull(get(created.id))
    }
    fun thumbnail(id: String, mode: ThumbnailMode, focusX: Double = .5, focusY: Double = .5) {
        get(id)?.let { save(it.copy(thumbnailMode = mode, focusX = focusX.coerceIn(0.0, 1.0), focusY = focusY.coerceIn(0.0, 1.0))) }
    }
    fun scale(id:String, mode:MediaScale) { get(id)?.let { save(it.copy(scale=mode)) } }
    fun get(id:String?)=state.value.firstOrNull { it.id==id }
    fun file(entry:MediaEntry, video:Boolean=false):File? {
        val relative=if(video)entry.video ?: return null else entry.primary
        if(entry.kind==MediaKind.WEB)return null
        return runCatching { if(entry.pluginId!=null) plugins.existingResource(entry.pluginId,relative) else scoped(relative).takeIf { it.isFile } }.getOrNull()
    }
    private fun scoped(path:String):File { require(path.isNotBlank() && !path.contains(':') && !path.contains('\\') && !path.startsWith('/') && path.split('/').none { it in setOf("",".","..") })
        return File(root,path).canonicalFile.also { require(it.toPath().startsWith(root.canonicalFile.toPath())) } }
    @Synchronized private fun save(entry:MediaEntry):MediaEntry {
        val updated=entry.copy(created=maxOf(entry.created,(get(entry.id)?.created ?: -1)+1))
        val entries=listOf(updated)+state.value.filterNot { it.id==entry.id }; persist(entries);state.value=entries;return updated
    }
    internal fun registerPluginMedia(entry:MediaEntry):MediaEntry {
        require(entry.pluginId!=null&&get(entry.id).let {it==null||it.pluginId==entry.pluginId})
        require(file(entry)!=null);return save(entry)
    }
    internal fun touchMedia(id:String):MediaEntry=save(requireNotNull(get(id)))
    internal fun presentation(entry:MediaEntry):MediaEntry {require(get(entry.id)!=null);return save(entry)}
    private fun persist(entries:List<MediaEntry>) { val stage=File(root,".catalog.tmp"); stage.writeText(JSONArray().also { array -> entries.forEach { array.put(it.json()) } }.toString(),Charsets.UTF_8)
        java.nio.file.Files.move(stage.toPath(),catalog.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
    fun import(uri:Uri, forcedMime:String?=null):MediaEntry {
        val name=runCatching { context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { if(it.moveToFirst())it.getString(0) else null } }.getOrNull()
            ?: uri.takeIf { it.scheme == "file" }?.lastPathSegment ?: "媒体"
        val mime=forcedMime ?: context.contentResolver.getType(uri)?.takeUnless { it=="application/octet-stream" }
            ?: android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.').lowercase()) ?: "application/octet-stream"
        val id=UUID.randomUUID().toString(); val directory=File(root,id).apply { mkdirs() }
        val target=File(directory,"source."+when { mime.startsWith("video/")->"mp4"; mime.startsWith("audio/")-> android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "audio"; mime in setOf("text/html","application/xhtml+xml")->"html"; mime.contains("heic")||mime.contains("heif")->"heic"; mime.contains("png")->"png"; mime.contains("webp")->"webp"; mime.contains("gif")->"gif"; else->"jpg" })
        try {
            require(mime.startsWith("image/")||mime.startsWith("video/")||mime.startsWith("audio/")||mime in setOf("text/html","application/xhtml+xml")) { context.uiCopy("media.unsupported_type") }
            requireNotNull(context.contentResolver.openInputStream(uri)).use { input -> target.outputStream().use { output ->
                val buffer=ByteArray(64*1024); var bytes=0L
                while(true) { val count=input.read(buffer);if(count<0)break;bytes+=count;require(bytes<=if(mime.contains("html"))2L*1024*1024 else 2L*1024*1024*1024) { context.uiCopy("media.file_too_large") };output.write(buffer,0,count) };require(bytes>0) }
            }
            var kind=when { mime.startsWith("video/")->MediaKind.VIDEO;mime.startsWith("audio/")->MediaKind.AUDIO;mime.contains("html")->MediaKind.HTML;else->MediaKind.IMAGE }
            val clip=File(directory,"motion.mp4"); if(kind==MediaKind.IMAGE && MotionPhoto.extract(target,clip))kind=MediaKind.MOTION_PHOTO
            return save(MediaEntry(id,name,kind,target.relativeTo(root).invariantSeparatorsPath,mime,
                clip.takeIf(File::isFile)?.relativeTo(root)?.invariantSeparatorsPath,network=kind==MediaKind.HTML))
        } catch(error:Exception) { directory.deleteRecursively();throw error }
    }
    fun pair(photoId:String, uri:Uri):MediaEntry {
        require(context.getSharedPreferences("experiments",0).getBoolean("apple-live-photos",false)) { context.uiCopy("media.enable_apple_live") }
        val photo=requireNotNull(get(photoId));require(photo.kind in setOf(MediaKind.IMAGE,MediaKind.MOTION_PHOTO)&&photo.pluginId==null)
        val clip=import(uri,"video/mp4")
        return save(photo.copy(kind=MediaKind.MOTION_PHOTO,video=clip.primary,appleLive=true))
    }
    fun html(name:String, text:String, id:String?=null):MediaEntry {
        require(text.toByteArray().size<=2*1024*1024)
        val key=id ?: UUID.randomUUID().toString(); val existing=get(key);require(existing==null || existing.pluginId==null&&existing.kind==MediaKind.HTML)
        val target=scoped("$key/index.html").apply { parentFile!!.mkdirs() }; val stage=File(target.parentFile,".index.tmp");stage.writeText(text,Charsets.UTF_8)
        java.nio.file.Files.move(stage.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        return save(MediaEntry(key,name.ifBlank { "HTML" },MediaKind.HTML,target.relativeTo(root).invariantSeparatorsPath,"text/html",network=true,
            created = maxOf(System.currentTimeMillis(), (existing?.created ?: 0) + 1)))
    }
    fun web(name:String,url:String,owner:String?=null):MediaEntry {
        val uri = try {
            java.net.URI(url)
        } catch (error: java.net.URISyntaxException) {
            throw IllegalArgumentException(context.uiCopy("media.invalid_web_address"), error)
        }
        require(uri.scheme == "https" && uri.host != null && uri.userInfo == null) { context.uiCopy("media.invalid_web_address") }
        return save(MediaEntry(UUID.randomUUID().toString(),name.ifBlank { uri.host },MediaKind.WEB,url,pluginId=owner,network=true))
    }
    fun plugin(owner:String, content:SubtitleContent):MediaEntry {
        fun resource(ref:PluginResource):String { require(ref.pluginId==null||ref.pluginId==owner);require(plugins.existingResource(owner,ref.path)!=null) { "插件媒体资源不存在" };return ref.path }
        val id=UUID.randomUUID().toString()
        return when(content) {
            is SubtitleContent.Image -> {
                val image=resource(content.source)
                val motion=content.motionVideo?.let(::resource) ?: run {
                    val path="resources/motion/$id.mp4";val destination=plugins.storage(owner).file(path)
                    if(MotionPhoto.locate(requireNotNull(plugins.existingResource(owner,image)))!=null) {
                        destination.parentFile!!.mkdirs();if(MotionPhoto.extract(requireNotNull(plugins.existingResource(owner,image)),destination))path else null
                    } else null
                }
                save(MediaEntry(id,content.description.ifBlank { "插件图片" },if(motion!=null)MediaKind.MOTION_PHOTO else MediaKind.IMAGE,image,
                    video=motion,pluginId=owner,appleLive=content.appleLive))
            }
            is SubtitleContent.Video -> save(MediaEntry(id,content.description.ifBlank { "插件视频" },MediaKind.VIDEO,resource(content.source),content.mimeType,pluginId=owner))
            is SubtitleContent.Html -> {
                require(content.allowedActions.size<=32&&content.allowedActions.all { it.length in 1..256 })
                val path=content.source?.let { require(requireNotNull(plugins.existingResource(owner,resource(it))).length()<=2*1024*1024);resource(it) } ?: "resources/subtitle-web/$id/index.html".also { relative ->
                    require(content.html.toByteArray().size<=128*1024) { "内联 HTML 超过 128 KiB，请使用插件资源文件" };plugins.storage(owner).file(relative).apply { parentFile!!.mkdirs();writeText(content.html,Charsets.UTF_8) }
                }
                save(MediaEntry(id,content.description,MediaKind.HTML,path,"text/html",pluginId=owner,actions=content.allowedActions,network=content.allowNetwork))
            }
            is SubtitleContent.Web -> web(content.description,content.url,owner)
            is SubtitleContent.Text -> error("文本不存入媒体库")
        }
    }
    @Synchronized fun remove(id:String) {
        val entries=state.value.filterNot { it.id==id };persist(entries);state.value=entries
        thumbnails.remove(id)
        File(root,"web-cache/$id").takeIf { it.isDirectory&&it.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()) }?.deleteRecursively()
        val used=state.value.filter { it.pluginId==null&&it.kind!=MediaKind.WEB }.flatMap { listOfNotNull(it.primary,it.video) }.map { it.substringBefore('/') }.toSet()
        root.listFiles().orEmpty().filter { it.isDirectory&&it.name.matches(Regex("[a-f0-9-]{36}"))&&it.name !in used }.forEach { it.deleteRecursively() }
    }
}
