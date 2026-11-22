package com.kigtts.next.plugins.control

import com.kigtts.plugin.api.PluginStorage
import java.io.File
import java.util.UUID

/** Cross-plugin transfer copies bytes, never grants the recipient a handle to the sender's directory. */
internal object ScopedResourceDelivery {
    fun copy(sender: PluginStorage, recipient: PluginStorage, path: String): String {
        val source = sender.file(path)
        require(source.exists()) { "草稿不存在" }
        require(source.isDirectory || !source.extension.equals("kiglua", ignoreCase = true)) { "Choose a Lua source ZIP" }
        val files = if (source.isDirectory) source.walkTopDown().toList() else listOf(source)
        require(files.size <= 256 && files.filter { it.isFile }.sumOf { it.length() } <= 16L * 1024 * 1024) { "资源过大，请减少文件" }
        val prefix = source.canonicalFile.toPath()
        files.forEach { file ->
            require(!java.nio.file.Files.isSymbolicLink(file.toPath()) && file.canonicalFile.toPath().startsWith(prefix)) { "资源包含外部链接" }
        }
        val relative = "inbox/${UUID.randomUUID()}/" + if (source.isDirectory) "draft" else "project.zip"
        val target = recipient.file(relative)
        try {
            files.forEach { file ->
                val destination = if (source.isDirectory) File(target, file.relativeTo(source).invariantSeparatorsPath) else target
                if (file.isDirectory) require(destination.mkdirs() || destination.isDirectory)
                else { destination.parentFile!!.mkdirs(); file.copyTo(destination, overwrite = false) }
            }
        } catch (error: Exception) { target.deleteRecursively(); throw error }
        return relative
    }
}
