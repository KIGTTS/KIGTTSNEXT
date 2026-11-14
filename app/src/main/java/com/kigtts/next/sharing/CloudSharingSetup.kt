package com.kigtts.next.sharing

import android.content.SharedPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class CloudSharingSetupState(val busy: Boolean = false, val error: String? = null,
    val progress: Float? = null)
/** One explicit choice precedes downloading, installation and all outgoing cloud use. */
internal class CloudSharingSetup(private val preferences: SharedPreferences, private val scope: CoroutineScope,
    private val ready: () -> Boolean, private val installed: () -> Boolean,
    private val install: suspend (progress: (Float?) -> Unit) -> Unit,
    private val installTimeoutMillis: Long = 60_000) {
    private val mutable = MutableStateFlow<CloudSharingSetupState?>(null)
    val state = mutable.asStateFlow()
    val enabled get() = preferences.getBoolean("cloud", false)
    private var pending: CompletableDeferred<Boolean>? = null
    private var job: Job? = null
    suspend fun ensure(): Boolean {
        if (preferences.contains("cloud")) {
            if (!enabled) return false
            if (ready()) return true
            // Disabling an installed plugin continues to mean system sharing.
            if (installed()) return false
        }
        return prompt()
    }
    /** Turning on receiving is an explicit opt-in even after choosing system sharing earlier. */
    suspend fun ensureForReceiving(): Boolean {
        if (enabled && ready()) return true
        return prompt()
    }
    private suspend fun prompt(): Boolean = (pending ?: CompletableDeferred<Boolean>().also {
            pending = it; mutable.value = CloudSharingSetupState()
        }).await()
    fun useCloud() {
        if (job?.isActive == true) return
        mutable.value = CloudSharingSetupState(busy = true, progress = 0f)
        job = scope.launch {
            try {
                withTimeout(installTimeoutMillis) {
                    val attempt = currentCoroutineContext()[Job]!!
                    install { value ->
                        if (attempt.isActive) mutable.value = CloudSharingSetupState(busy = true,
                            progress = value?.coerceIn(0f, 1f))
                    }
                }
                currentCoroutineContext().ensureActive()
                check(ready()) { "云共享暂时无法启动，可以先使用系统分享" }
                preferences.edit().putBoolean("cloud", true).apply()
                mutable.value = null; pending?.complete(true); pending = null
            } catch (timeout: TimeoutCancellationException) {
                mutable.value = CloudSharingSetupState(error = "启用超时，请重试或使用系统分享")
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { mutable.value = CloudSharingSetupState(error = error.message ?: "安装未完成，请重试") }
        }
    }
    fun useSystem() {
        job?.cancel(); job = null
        preferences.edit().putBoolean("cloud", false).apply()
        mutable.value = null; pending?.complete(false); pending = null
    }
    fun dismiss() {
        job?.cancel(); job = null
        mutable.value = null; pending?.cancel(); pending = null
    }
    fun close() { job?.cancel(); pending?.cancel(); pending = null }
}
