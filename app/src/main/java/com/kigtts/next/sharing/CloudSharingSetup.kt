package com.kigtts.next.sharing

import android.content.SharedPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class CloudSharingSetupState(val busy: Boolean = false, val error: String? = null)
/** One explicit choice precedes downloading, installation and all outgoing cloud use. */
internal class CloudSharingSetup(private val preferences: SharedPreferences, private val scope: CoroutineScope,
    private val ready: () -> Boolean, private val installed: () -> Boolean, private val install: suspend () -> Unit) {
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
        return (pending ?: CompletableDeferred<Boolean>().also {
            pending = it; mutable.value = CloudSharingSetupState()
        }).await()
    }
    fun useCloud() {
        if (job?.isActive == true) return
        mutable.value = CloudSharingSetupState(busy = true)
        job = scope.launch {
            try {
                install()
                check(ready()) { "云共享暂时无法启动，可以先使用系统分享" }
                preferences.edit().putBoolean("cloud", true).apply()
                mutable.value = null; pending?.complete(true); pending = null
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { mutable.value = CloudSharingSetupState(error = error.message ?: "安装未完成，请重试") }
        }
    }
    fun useSystem() {
        if (mutable.value?.busy == true) return
        preferences.edit().putBoolean("cloud", false).apply()
        mutable.value = null; pending?.complete(false); pending = null
    }
    fun dismiss() {
        if (mutable.value?.busy == true) return
        mutable.value = null; pending?.cancel(); pending = null
    }
    fun close() { job?.cancel(); pending?.cancel(); pending = null }
}
