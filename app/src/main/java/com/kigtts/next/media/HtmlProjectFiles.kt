package com.kigtts.next.media

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class HtmlProjectItem(val path: String, val name: String, val directory: Boolean, val size: Long, val main: Boolean) {
    val editable get() = !directory && name.substringAfterLast('.', "").lowercase() in setOf("html", "htm", "css", "js", "json", "txt", "svg", "xml", "md")
}

/** Host HTML projects have one guarded root, independent of the selected entry page's directory. */
class HtmlProjectFiles(private val repository: MediaRepository) {
    fun root(id: String): File {
        val entry = requireNotNull(repository.get(id)) { "页面不存在" }
        require(entry.kind == MediaKind.HTML && entry.pluginId == null) { "请先复制为自己的页面" }
        val name = entry.primary.substringBefore('/')
        require(name.matches(Regex("[a-f0-9-]{36}")))
        return File(repository.root, name).canonicalFile.also { require(it.toPath().startsWith(repository.root.canonicalFile.toPath())) }
    }
    fun file(id: String, path: String): File {
        require(path.isEmpty() || path.split('/').all { validName(it) }) { "名称不能包含路径或特殊字符" }
        val root = root(id)
        return File(root, path).canonicalFile.also { require(it.toPath().startsWith(root.toPath())) { "文件超出页面目录" } }
    }
    fun main(id: String) = requireNotNull(repository.get(id)).primary.substringAfter('/')
    fun list(id: String, directory: String): List<HtmlProjectItem> = file(id, directory).also { require(it.isDirectory) }.listFiles().orEmpty()
        .filter { !it.name.startsWith(".kigtts-") }.map { f -> HtmlProjectItem(f.relativeTo(root(id)).invariantSeparatorsPath,
            f.name, f.isDirectory, f.length(), f.relativeTo(root(id)).invariantSeparatorsPath == main(id)) }
        .sortedWith(compareBy<HtmlProjectItem> { !it.directory }.thenBy { it.name.lowercase() })
    fun create(id: String, directory: String, name: String, folder: Boolean) {
        require(validName(name)) { "请输入有效名称" }
        val parent = file(id, directory); require(parent.isDirectory)
        val target = file(id, join(directory, name)); require(!target.exists()) { "同名文件已存在" }
        if (folder) require(target.mkdir()) { "无法创建文件夹" }
        else target.writeText(if(name.endsWith(".html",true)) TEMPLATE else "", Charsets.UTF_8)
        touch(id)
    }
    fun read(id: String, path: String): String = file(id, path).also { require(it.isFile && it.length() <= TEXT_LIMIT) { "此文件过大，无法编辑" } }.readText(Charsets.UTF_8)
    fun write(id: String, path: String, text: String) {
        require(text.toByteArray(Charsets.UTF_8).size <= TEXT_LIMIT) { "文件内容过长" }
        val target = file(id, path); require(path.isNotEmpty() && target.isFile)
        val stage = File(target.parentFile, ".kigtts-save-${java.util.UUID.randomUUID()}")
        try { stage.writeText(text, Charsets.UTF_8); Files.move(stage.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        finally { stage.delete() }
        touch(id)
    }
    fun selectMain(id: String, path: String) {
        val f = file(id,path); require(f.isFile && f.extension.lowercase() in setOf("html", "htm")) { "请选择 HTML 页面" }
        repository.updateHtml(id, root(id).name + "/" + path)
    }
    fun rename(id: String, path: String, name: String) {
        require(validName(name)) { "请输入有效名称" }
        relocate(id,path,join(path.substringBeforeLast('/', ""),name),copy=false)
    }
    fun move(id: String, paths: List<String>, directory: String, copy: Boolean) {
        require(file(id,directory).isDirectory)
        val sources = topLevel(paths)
        sources.forEach { path ->
            require(directory != path && !directory.startsWith("$path/")) { "不能移入自身目录" }
            require(path.substringBeforeLast('/', "") != directory || copy) { "文件已在此目录" }
        }
        sources.forEach { path -> relocate(id,path,unique(id,directory,file(id,path).name),copy) }
    }
    private fun relocate(id: String, path: String, destination: String, copy: Boolean) {
        require(path.isNotEmpty()); val source = file(id,path); val target = file(id,destination)
        require(source.exists() && !target.exists()) { "同名文件已存在" }
        require(target.parentFile!!.isDirectory && !target.toPath().startsWith(source.toPath()))
        if(copy) copyDirectory(source,target) else Files.move(source.toPath(),target.toPath())
        val old = main(id)
        if(!copy && (old == path || old.startsWith("$path/"))) selectMain(id,destination+old.removePrefix(path)) else touch(id)
    }
    fun delete(id: String, paths: List<String>) {
        val sources = topLevel(paths); val entry = main(id)
        require(sources.none { it.isEmpty() || entry == it || entry.startsWith("$it/") }) { "请先指定另一个主页面" }
        sources.forEach { require(file(id,it).deleteRecursively()) { "无法删除文件" } };touch(id)
    }
    fun unique(id: String, directory: String, name: String): String {
        require(validName(name)); var candidate = join(directory,name); var n = 2
        while(file(id,candidate).exists()) {
            val base = name.substringBeforeLast('.', name); val ext = name.substringAfterLast('.', "")
            candidate = join(directory, "$base (${n++})" + if(ext.isBlank()) "" else ".$ext")
        }
        return candidate
    }
    fun touch(id: String) = repository.updateHtml(id, requireNotNull(repository.get(id)).primary)
    companion object {
        const val TEXT_LIMIT = 2 * 1024 * 1024
        const val TEMPLATE = "<!doctype html>\n<html lang=\"zh\">\n<head>\n<meta charset=\"utf-8\">\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n<title>新页面</title>\n</head>\n<body>\n<h1>你好</h1>\n</body>\n</html>\n"
        fun validName(name: String) = name.isNotBlank() && name.length <= 180 && name !in setOf(".","..") &&
            name.none { it in "/\\:*?\"<>|" || it.code < 32 } && !name.startsWith(".kigtts-")
        fun join(directory: String, name: String) = if(directory.isEmpty())name else "$directory/$name"
        private fun topLevel(paths: List<String>) = paths.distinct().filter { path -> paths.none { it != path && path.startsWith("$it/") } }
        internal fun copyDirectory(source: File, target: File) {
            val base = source.canonicalFile.toPath()
            var total = 0L; var count = 0
            try { source.walkTopDown().forEach { f ->
                require(++count <= 2048 && f.canonicalFile.toPath().startsWith(base)) { "页面目录过大或包含外部链接" }
                val output = if(f == source)target else File(target,f.relativeTo(source).path)
                if(f.isDirectory)require(output.mkdirs() || output.isDirectory) else {
                    total += f.length();require(total <= 2L*1024*1024*1024) { "页面资源过大" }; f.copyTo(output)
                }
            } } catch(e:Exception) { target.deleteRecursively();throw e }
        }
    }
}
