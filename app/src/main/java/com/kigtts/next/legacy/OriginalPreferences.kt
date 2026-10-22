package com.kigtts.next.legacy

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID

/** Copy before reading; the original application's DataStore is never opened for writing. */
internal object OriginalPreferences {
    suspend fun read(context: Context, source: File): LegacyContent = withContext(Dispatchers.IO) {
        require(source.length() <= LegacyArchive.MAX_METADATA) { "旧版设置文件过大" }
        val snapshot = File(context.cacheDir, "legacy-${UUID.randomUUID()}.preferences_pb")
        source.copyTo(snapshot)
        val job = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO), produceFile = { snapshot })
            val values = store.data.first().asMap().entries.filter { it.value is String }.associate { it.key.name to it.value as String }
            LegacyContentDecoder.configs(values) { path ->
                val file = runCatching { File(path.removePrefix("file://")).canonicalFile }.getOrNull()
                file?.takeIf { it.isFile && (it.toPath().startsWith(File(context.filesDir, "quick_cards").canonicalFile.toPath()) ||
                    it.toPath().startsWith(File(context.filesDir, "soundboard/audio").canonicalFile.toPath())) }
            }
        } finally { job.cancelAndJoin(); snapshot.delete() }
    }
}
