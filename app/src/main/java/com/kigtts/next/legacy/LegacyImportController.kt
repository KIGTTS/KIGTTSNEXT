package com.kigtts.next.legacy

import android.content.Context
import android.net.Uri
import com.kigtts.next.cards.CardRepository
import com.kigtts.next.media.MediaRepository
import com.kigtts.next.state.QuickTextLibrary
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

internal class LegacyImportController(private val context: Context, library: QuickTextLibrary, media: MediaRepository,
    cards: CardRepository, private val scope: CoroutineScope, private val notify: (String) -> Unit) {
    private val migration = LegacyMigration(library, media, cards)
    private val prepared = MutableStateFlow<LegacyContent?>(null)
    private val running = MutableStateFlow(false)
    val preview = prepared.asStateFlow()
    val busy = running.asStateFlow()
    private var staging: File? = null
    fun prepare(uri: Uri): Job? {
        if (running.value) return null
        running.value = true
        return scope.launch {
            try {
                discard()
                val directory = File(context.cacheDir, "legacy-import/${UUID.randomUUID()}").also { staging = it }
                prepared.value = withContext(Dispatchers.IO) {
                    directory.mkdirs()
                    val archive = File(directory, "archive")
                    requireNotNull(context.contentResolver.openInputStream(uri)) { "无法读取备份" }.use { input ->
                        archive.outputStream().use { output -> LegacyArchive.copy(input, output, LegacyArchive.MAX_BYTES) }
                    }
                    LegacyArchive.read(archive, File(directory, "content"))
                }
            } catch (cancel: CancellationException) { discard(); throw cancel }
            catch (error: Exception) { discard(); notify(error.message ?: "无法读取旧版备份") }
            finally { running.value = false }
        }
    }
    fun confirm() {
        if (running.value) return
        val value = prepared.value ?: return
        running.value = true
        scope.launch {
            try {
                val (phrases, photos) = migration.apply(value)
                notify(if (phrases + photos == 0) "这些内容已经导入" else "已导入 $phrases 条快捷文本、$photos 张拍立得")
                discard()
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { notify(error.message ?: "导入失败，可重试") }
            finally { running.value = false }
        }
    }
    fun cancel() { if (!running.value) scope.launch { discard() } }
    private suspend fun discard() {
        prepared.value = null
        val previous = staging; staging = null
        withContext(NonCancellable + Dispatchers.IO) { previous?.deleteRecursively() }
    }
    fun migrateOriginalInstallation() {
        val preferences = context.getSharedPreferences("original-kigtts-migration", 0)
        if (preferences.getBoolean("completed-v1", false)) return
        val source = File(context.filesDir, "datastore/user_prefs.preferences_pb")
        if (!source.isFile || running.value) return
        running.value = true
        scope.launch {
            try {
                val content = OriginalPreferences.read(context, source)
                val (phrases, photos) = migration.apply(content)
                check(preferences.edit().putBoolean("completed-v1", true).commit())
                if (phrases + photos > 0) notify("已迁移旧版快捷文本、音效和名片，原文件已保留")
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { notify("旧版数据未迁移完成，可在备份与还原中导入：${error.message}") }
            finally { running.value = false }
        }
    }
}
