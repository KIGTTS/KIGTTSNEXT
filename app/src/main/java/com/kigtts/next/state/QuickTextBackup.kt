package com.kigtts.next.state

import android.content.ContentResolver
import android.net.Uri
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PluginPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Native backup uses the same bounded archive parser and revisioned review as plugins. */
internal class QuickTextBackup(private val model: AppViewModel) {
    private val storage = model.pluginManager.paths.storage(PluginPaths.HOST_BACKUP_ID)
    private val broker = model.quickTextPlugins.forPlugin(PluginPaths.HOST_BACKUP_ID,
        setOf("quick-text-read", "quick-text-write", "network"))
    suspend fun prepareImport(resolver: ContentResolver, uri: Uri): QuickTextReply {
        val path = "imports/${UUID.randomUUID()}.kigcards"
        val source = storage.file(path)
        try {
            withContext(Dispatchers.IO) {
                source.parentFile!!.mkdirs()
                requireNotNull(resolver.openInputStream(uri)) { "无法读取文件" }.use { input ->
                    source.outputStream().use { output -> copyBounded(input, output) }
                }
            }
            return broker.request(QuickTextCommand.Import(PluginResource(path), UUID.randomUUID().toString(), model.quickTexts.revision.value))
        } finally { withContext(Dispatchers.IO) { source.delete() } }
    }
    suspend fun prepareExport(): File {
        val path = "exports/${UUID.randomUUID()}.kigcards"
        return when (val reply = broker.request(QuickTextCommand.Export(destination = path))) {
            is QuickTextReply.Resource -> storage.file(reply.source.path)
            is QuickTextReply.Failure -> error(reply.message)
            else -> error("无法导出快捷文本")
        }
    }
    suspend fun saveExport(resolver: ContentResolver, uri: Uri, path: String) = withContext(Dispatchers.IO) {
        val source = exportFile(path)
        requireNotNull(resolver.openOutputStream(uri, "wt")) { "无法写入文件" }.use { output ->
            source.inputStream().use { input -> copyBounded(input, output) }
        }
    }
    fun exportFile(path: String): File {
        require(path.startsWith("exports/") && path.endsWith(".kigcards"))
        return storage.file(path)
    }
    private fun copyBounded(input: java.io.InputStream, output: java.io.OutputStream) {
        var total = 0L
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= 2L * 1024 * 1024 * 1024) { "备份文件不能超过 2 GiB，请分批导出" }
            output.write(buffer, 0, count)
        }
    }
}
