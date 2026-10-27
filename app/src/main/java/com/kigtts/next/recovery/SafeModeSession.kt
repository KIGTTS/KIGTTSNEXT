package com.kigtts.next.recovery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

internal data class SafeModeStatus(val active: Boolean, val prompt: Boolean = active,
    val restoring: Boolean = false, val error: String? = null)

/** One notice per UI session; acknowledging it never changes plugin enable preferences. */
internal class SafeModeSession(private val recovery: CrashRecovery, private val reload: suspend () -> Unit) {
    private val mutable = MutableStateFlow(SafeModeStatus(recovery.safeMode()))
    val state = mutable.asStateFlow()
    private val restoreLock = Mutex()
    fun synchronize() {
        val active = recovery.safeMode()
        mutable.update { it.copy(active = active, prompt = active && (!it.active || it.prompt)) }
    }
    fun enterSoftware() { mutable.update { it.copy(prompt = false) } }
    suspend fun restorePlugins() {
        if (!restoreLock.tryLock()) return
        try {
            mutable.update { it.copy(restoring = true, error = null) }
            withContext(Dispatchers.IO) { recovery.restore() }
            reload()
            synchronize()
        } catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) { synchronize(); mutable.update { it.copy(prompt = true, error = error.message ?: "插件未能恢复，请重试") } }
        finally { mutable.update { it.copy(restoring = false) }; restoreLock.unlock() }
    }
}
