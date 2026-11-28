package com.kigtts.next.updates

import android.app.Application
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal enum class UpdateResult { IDLE, CHECKING, CURRENT, AVAILABLE, FAILED, IGNORED }

internal data class UpdateStatus(
    val automatic: Boolean,
    val release: AppRelease? = null,
    val result: UpdateResult = UpdateResult.IDLE,
    val pending: Boolean = false,
    val manual: Boolean = false,
)

internal class AppUpdates(
    private val prefs: SharedPreferences,
    private val scope: CoroutineScope,
    private val installed: ReleaseVersion?,
    private val source: AppUpdateSource = AppUpdateSource(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    constructor(app: Application, scope: CoroutineScope) : this(
        app.getSharedPreferences("app-updates", 0), scope, installedVersion(app)
    )

    private var ignored = prefs.getString("ignored", null)
    private var deferred: ReleaseVersion? = null
    private val status = MutableStateFlow(UpdateStatus(prefs.getBoolean("automatic", true), restore()))
    val state = status.asStateFlow()

    fun automatic(enabled: Boolean) {
        prefs.edit().putBoolean("automatic", enabled).apply()
        val current = status.value
        status.value = current.copy(automatic = enabled, pending = current.pending && (enabled || current.manual))
    }

    fun check(manual: Boolean = false) {
        val initial = status.value
        if (initial.result == UpdateResult.CHECKING || (!manual && !initial.automatic)) return
        if (!manual && now() - prefs.getLong("last-check", 0L) in 0 until CHECK_INTERVAL_MS) {
            if (initial.release != null && initial.release.version != deferred)
                status.value = initial.copy(pending = true, manual = false)
            return
        }
        status.value = initial.copy(result = UpdateResult.CHECKING, pending = false, manual = false)
        scope.launch {
            val result = withContext(Dispatchers.IO) { source.latest() }
            val current = status.value
            result.fold(onSuccess = { release ->
                val newer = release?.takeIf { isNewer(it) }
                prefs.edit().putLong("last-check", now()).putString("release", newer?.let {
                    JSONObject().put("version", it.version.toString()).put("notes", it.notes).toString()
                }).apply()
                val visible = newer?.takeIf { it.version.toString() != ignored }
                status.value = current.copy(release = visible, result = when {
                    visible != null -> UpdateResult.AVAILABLE
                    newer != null -> UpdateResult.IGNORED
                    else -> UpdateResult.CURRENT
                }, pending = visible != null && (manual || current.automatic && visible.version != deferred), manual = manual)
            }, onFailure = {
                status.value = current.copy(result = UpdateResult.FAILED,
                    pending = current.release != null && !manual && current.automatic && current.release.version != deferred)
            })
        }
    }

    fun showAvailable() {
        val current = status.value
        if (current.release != null) status.value = current.copy(pending = true, manual = true)
    }

    fun later() {
        deferred = status.value.release?.version
        status.value = status.value.copy(pending = false, manual = false)
    }

    fun ignore() {
        ignored = status.value.release?.version?.toString() ?: return
        prefs.edit().putString("ignored", ignored).apply()
        status.value = status.value.copy(release = null, pending = false, manual = false, result = UpdateResult.IGNORED)
    }

    private fun isNewer(release: AppRelease) = installed != null && release.version > installed

    private fun restore(): AppRelease? = prefs.getString("release", null)?.let { data ->
        runCatching { parseMirrorRelease(JSONObject(data)) }.getOrNull()
            ?.takeIf { isNewer(it) && it.version.toString() != ignored }
    }

    private companion object {
        const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

        @Suppress("DEPRECATION")
        fun installedVersion(app: Application) =
            ReleaseVersion.parse(app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty())
    }
}
