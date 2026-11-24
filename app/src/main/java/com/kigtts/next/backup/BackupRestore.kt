package com.kigtts.next.backup

import android.app.Application
import android.net.Uri
import com.kigtts.next.legacy.LegacyArchive
import com.kigtts.next.legacy.legacyJson
import com.kigtts.next.state.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

internal class BackupRestore(private val model: AppViewModel) {
    private val context = model.getApplication<Application>()
    private val config = AppConfigArchive(context)
    private val pending = MutableStateFlow<ConfigRestore?>(null)
    val pendingConfig = pending.asStateFlow()
    private val restoring = MutableStateFlow(false)
    val busy = restoring.asStateFlow()

    suspend fun offer(uri: Uri) {
        val root = File(context.cacheDir, "backup-restore").apply { mkdirs() }
        val file = File.createTempFile("read-", ".backup", root)
        try {
            withContext(Dispatchers.IO) {
                requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                    file.outputStream().use { LegacyArchive.copy(input, it, LegacyArchive.MAX_BYTES) }
                }
            }
            offerFile(file)
        } finally { withContext(NonCancellable + Dispatchers.IO) { file.delete() } }
    }

    suspend fun offerFile(file: File) {
        check(restoring.compareAndSet(expect = false, update = true))
        try {
            val type = withContext(Dispatchers.IO) { detect(file) }
            when (type) {
                BackupType.QUICK -> model.quickTextBackup.offerImport(context.contentResolver, Uri.fromFile(file))
                BackupType.LEGACY -> model.legacyImports.prepare(file)
                BackupType.CONFIG -> {
                    val staging = File(context.cacheDir, "backup-restore/config-${UUID.randomUUID()}")
                    try {
                        val preview = withContext(Dispatchers.IO) { config.read(file, staging.apply { mkdirs() }) }
                        discardConfig()
                        pending.value = preview
                    } catch (error: Exception) {
                        withContext(NonCancellable + Dispatchers.IO) { staging.deleteRecursively() }
                        throw error
                    }
                }
            }
        } finally { restoring.value = false }
    }

    suspend fun confirmConfig() {
        val preview = pending.value ?: return
        check(restoring.compareAndSet(expect = false, update = true))
        try {
            model.stopSpeech()
            withContext(Dispatchers.IO) { config.restore(preview) }
            model.reloadBackedUpSpeechSettings()
            model.reloadBackedUpSettings()
            discardConfig()
        } finally { restoring.value = false }
    }

    suspend fun discardConfig() {
        val staging = pending.value?.staging
        pending.value = null
        withContext(NonCancellable + Dispatchers.IO) { staging?.deleteRecursively() }
    }

    suspend fun prepareConfigExport(): File = withContext(Dispatchers.IO) {
        val root = File(context.cacheDir, "backup-exports").apply { mkdirs() }
        File.createTempFile("settings-", ".kigconfig", root).also {
            try { config.write(it) } catch (error: Exception) { it.delete(); throw error }
        }
    }

    fun configExportFile(name: String): File {
        require(name.matches(Regex("settings-[0-9]+\\.kigconfig")))
        return File(context.cacheDir, "backup-exports/$name")
    }

    suspend fun saveConfigExport(uri: Uri, name: String) = withContext(Dispatchers.IO) {
        requireNotNull(context.contentResolver.openOutputStream(uri)).use { output ->
            configExportFile(name).inputStream().use { LegacyArchive.copy(it, output, LegacyArchive.MAX_BYTES) }
        }
    }

    private fun detect(file: File): BackupType {
        val zipped = file.inputStream().use { it.read() == 0x50 && it.read() == 0x4b }
        if (!zipped) return BackupType.LEGACY
        return ZipFile(file).use { zip ->
            require(zip.size() <= 12_000)
            val descriptor = zip.getEntry("manifest.json")
            val type = descriptor?.let { legacyJson(zip.readBackupText(it)).optString("type") }
            when {
                type == CONFIG_BACKUP_TYPE -> BackupType.CONFIG
                zip.getEntry("cards.json") != null -> BackupType.QUICK
                else -> BackupType.LEGACY
            }
        }
    }
}

private enum class BackupType { QUICK, LEGACY, CONFIG }
