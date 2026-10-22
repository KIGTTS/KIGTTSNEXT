package com.kigtts.next.plugins.control

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class StoreBootstrapPhase { OFFER, RESTORE, DOWNLOADING, REVIEW, INSTALLING, COMPLETED, ERROR }
internal enum class StoreBootstrapPurpose { SOURCE, SYNTHESIS }
internal data class StoreBootstrapState(val phase: StoreBootstrapPhase, val candidate: StoreDownload? = null, val error: String? = null,
    val purpose: StoreBootstrapPurpose = StoreBootstrapPurpose.SOURCE)

/** User consent precedes network access and native publisher trust precedes installation. */
internal class StoreBootstrapCoordinator(private val scope: CoroutineScope, private val installed: () -> Boolean,
    private val ready: () -> Boolean, private val download: suspend () -> StoreDownload,
    private val install: suspend (StoreDownload) -> Unit, private val restore: suspend () -> Unit,
    private val complete: () -> Unit, private val io: CoroutineDispatcher = Dispatchers.IO) {
    private val mutable = MutableStateFlow<StoreBootstrapState?>(null)
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var afterReady: () -> Unit = complete
    fun offer(purpose: StoreBootstrapPurpose = StoreBootstrapPurpose.SOURCE, onReady: () -> Unit = complete) {
        if (ready()) { onReady(); return }
        if (mutable.value == null) {
            afterReady = onReady
            mutable.value = StoreBootstrapState(if (installed()) StoreBootstrapPhase.RESTORE else StoreBootstrapPhase.OFFER, purpose = purpose)
        }
    }
    fun confirm() {
        val current = mutable.value ?: return
        if (job?.isActive == true) return
        if (ready()) { current.candidate?.file?.delete(); mutable.value = null; afterReady(); return }
        val candidate = current.candidate
        val restoring = installed() && candidate == null
        mutable.value = current.copy(phase = if (restoring || candidate != null) StoreBootstrapPhase.INSTALLING else StoreBootstrapPhase.DOWNLOADING, error = null)
        job = scope.launch {
            var received: StoreDownload? = null
            try {
                when {
                    restoring -> restore()
                    candidate != null -> install(candidate)
                    else -> {
                        withContext(io) { download().also { received = it } }
                        mutable.value = StoreBootstrapState(StoreBootstrapPhase.REVIEW, received, purpose = current.purpose)
                        return@launch
                    }
                }
                check(ready()) { "插件商店未能启动，请在插件管理中检查启用状态或安全模式" }
                if (candidate != null) mutable.value = current.copy(phase = StoreBootstrapPhase.COMPLETED)
                else { mutable.value = null; afterReady() }
            } catch (cancel: CancellationException) { received?.file?.delete(); throw cancel }
            catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                // Installation can succeed before a worker/startup error. Do not offer to reinstall it.
                val retained = candidate?.takeUnless { installed() }
                if (retained == null) candidate?.file?.delete()
                mutable.value = StoreBootstrapState(StoreBootstrapPhase.ERROR, retained, failure.message ?: "插件商店暂时无法安装，请稍后重试", current.purpose)
            } finally { if (!currentCoroutineContext().isActive) received?.file?.delete() }
        }
    }
    fun dismiss() {
        if (mutable.value?.phase == StoreBootstrapPhase.INSTALLING) return
        if (mutable.value?.phase == StoreBootstrapPhase.COMPLETED) { confirm(); return }
        job?.cancel(); mutable.value?.candidate?.file?.delete(); mutable.value = null
    }
    fun close() { job?.cancel(); mutable.value?.candidate?.file?.delete(); mutable.value = null }
}
