package com.kigtts.next.legacy

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID

/** Copy before reading; the original application's DataStore is never opened for writing. */
internal object OriginalPreferences {
    fun source(context: Context) = File(context.filesDir, "datastore/user_prefs.preferences_pb")
    fun exists(context: Context) = source(context).let { it.isFile && it.length() in 1..LegacyArchive.MAX_METADATA }

    suspend fun read(context: Context, source: File): LegacyContent = withContext(Dispatchers.IO) {
        require(source.length() <= LegacyArchive.MAX_METADATA) { "旧版设置文件过大" }
        val snapshot = File(context.cacheDir, "legacy-${UUID.randomUUID()}.preferences_pb")
        source.copyTo(snapshot)
        val job = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO), produceFile = { snapshot })
            val preferences = store.data.first().asMap().entries.associate { it.key.name to it.value }
            val values = preferences.mapNotNull { (name, value) -> (value as? String)?.let { name to it } }.toMap()
            val roots = listOf(File(context.filesDir, "quick_cards"), File(context.filesDir, "soundboard/audio"))
            LegacyContentDecoder.configs(values, recoverMissing = true) { path ->
                val name = path.removePrefix("file://")
                val local = sequenceOf(File(name), File(context.filesDir, name))
                val relocated = roots.asSequence().mapNotNull { root ->
                    val marker = if (root.name == "audio") "soundboard/audio/" else "quick_cards/"
                    name.substringAfter(marker, "").takeIf { it.isNotBlank() }?.let { File(root, it) }
                }
                (local + relocated).mapNotNull { runCatching { it.canonicalFile }.getOrNull() }.firstOrNull { file ->
                    file.isFile && roots.any { file.toPath().startsWith(it.canonicalFile.toPath()) }
                }
            }.copy(preferences = preferences, resourceRoot = context.filesDir)
        } finally { job.cancelAndJoin(); snapshot.delete() }
    }
}
