package com.kigtts.next.state

import android.content.ContentResolver
import android.net.Uri
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PluginPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID
import com.kigtts.next.plugins.quicktext.QuickCardsArchive
import com.kigtts.next.plugins.quicktext.dto

/** Native backup uses the same bounded archive parser and revisioned review as plugins. */
internal class QuickTextBackup(private val model: AppViewModel) {
    internal data class ImportSelection(val path: String, val groups: List<QuickGroup>)
    private val pending = kotlinx.coroutines.flow.MutableStateFlow<ImportSelection?>(null)
    val pendingImport = pending.asStateFlow()
    suspend fun offerImport(resolver: ContentResolver, uri: Uri) {
        val inspected = inspectImport(resolver, uri)
        pending.value?.let(::discardImport)
        pending.value = inspected
    }
    fun clearPending() { pending.value = null }
    private val storage = model.pluginManager.paths.storage(PluginPaths.HOST_BACKUP_ID)
    private val broker = model.quickTextPlugins.forPlugin(PluginPaths.HOST_BACKUP_ID,
        setOf("quick-text-read", "quick-text-write", "network"))
    suspend fun prepareImport(resolver: ContentResolver, uri: Uri): QuickTextReply {
        return try {
            val selection = inspectImport(resolver, uri)
            importSelected(selection, selection.groups.map { it.id }.toSet())
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (error: Exception) { QuickTextReply.Failure(QuickTextError.INVALID, error.message ?: "无法读取备份") }
    }
    suspend fun inspectImport(resolver: ContentResolver, uri: Uri): ImportSelection {
        val path = "imports/${UUID.randomUUID()}.kigcards"
        val source = storage.file(path)
        try {
            withContext(Dispatchers.IO) {
                source.parentFile!!.mkdirs()
                requireNotNull(resolver.openInputStream(uri)) { "无法读取文件" }.use { input ->
                    source.outputStream().use { output -> copyBounded(input, output) }
                }
            }
            val groups = withContext(Dispatchers.IO) { QuickArchiveSelection.read(source).groups }
            return ImportSelection(path, groups)
        } catch (error: Exception) { source.delete(); throw error }
    }
    suspend fun importSelected(selection: ImportSelection, groups: Set<String>): QuickTextReply {
        require(selection.path.startsWith("imports/"))
        val path = "imports/${UUID.randomUUID()}.kigcards"
        try {
            withContext(Dispatchers.IO) { QuickArchiveSelection.write(storage.file(selection.path), storage.file(path), groups) }
            return broker.request(QuickTextCommand.Import(PluginResource(path), UUID.randomUUID().toString(), model.quickTexts.revision.value))
        } finally { withContext(Dispatchers.IO) { storage.file(path).delete(); discardImport(selection) } }
    }
    fun discardImport(selection: ImportSelection) { require(selection.path.startsWith("imports/")); storage.file(selection.path).delete() }
    suspend fun prepareExport(groupIds: List<String> = emptyList(), entryIds: List<String> = emptyList()): File {
        val path = "exports/${UUID.randomUUID()}.kigcard"
        return when (val reply = broker.request(QuickTextCommand.Export(groupIds = groupIds, entryIds = entryIds, destination = path))) {
            is QuickTextReply.Resource -> storage.file(reply.source.path)
            is QuickTextReply.Failure -> error(reply.message)
            else -> error("无法导出快捷文本")
        }
    }
    suspend fun exportDraft(draft: PhraseDraft): File {
        require(draft.valid(model.mediaRepository)) { "请先补全快捷文本内容" }
        val snapshot = model.quickTextPlugins.snapshot()
        val phrase = QuickPhrase(draft.text, draft.category, draft.phraseId ?: UUID.randomUUID().toString(), draft.title,
            if (draft.type == QuickCardType.TEXT) null else draft.mediaId, draft.type, draft.colorMode, draft.color,
            draft.richText, draft.formats, draft.autoPlay, draft.audioMode, draft.metadata)
        val entry = phrase.dto(snapshot.groups, model.mediaRepository.get(phrase.mediaId))
        val path = "exports/${UUID.randomUUID()}.kigcard"
        return withContext(Dispatchers.IO) {
            QuickCardsArchive(model.quickTextPlugins, PluginPaths.HOST_BACKUP_ID, setOf("quick-text-read", "network"))
                .export(QuickTextCommand.Export(entryIds = listOf(entry.id), destination = path), snapshot.copy(entries = listOf(entry)))
            storage.file(path)
        }
    }
    suspend fun saveExport(resolver: ContentResolver, uri: Uri, path: String) = withContext(Dispatchers.IO) {
        val source = exportFile(path)
        requireNotNull(resolver.openOutputStream(uri, "wt")) { "无法写入文件" }.use { output ->
            source.inputStream().use { input -> copyBounded(input, output) }
        }
    }
    fun exportFile(path: String): File {
        require(path.startsWith("exports/") && (path.endsWith(".kigcards") || path.endsWith(".kigcard")))
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
