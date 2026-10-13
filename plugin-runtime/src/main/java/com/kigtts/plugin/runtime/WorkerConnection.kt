package com.kigtts.plugin.runtime

import android.content.*
import android.os.IBinder
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class WorkerConnection(private val context: Context, private val slot: Int, private val broker: IHostSpeechBroker? = null,
    private val quickText:IHostQuickTextBroker?=null, private val app: IHostAppBroker? = null,
    private val stickers: IHostStickerBroker? = null,
    private val uiChanged: () -> Unit = {}) {
    private val died = CompletableDeferred<Unit>()
    lateinit var worker: IPluginWorker
        private set
    private var connection: ServiceConnection? = null
    val alive get() = ::worker.isInitialized && !died.isCompleted && worker.asBinder().isBinderAlive
    suspend fun connect(id: String): PluginSnapshot = withTimeout(15_000) {
        suspendCancellableCoroutine { continuation ->
            val service = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    worker = IPluginWorker.Stub.asInterface(binder)
                    binder.linkToDeath({ died.complete(Unit) }, 0)
                    if (continuation.isActive) continuation.resume(Unit)
                }
                override fun onServiceDisconnected(name: ComponentName) { died.complete(Unit) }
                override fun onNullBinding(name: ComponentName) { if (continuation.isActive) continuation.resumeWithException(IllegalStateException("插件工作进程不可用")) }
            }
            connection = service
            if (!context.bindService(Intent(context, workerServices[slot]), service, Context.BIND_AUTO_CREATE)) {
                connection = null
                continuation.resumeWithException(IllegalStateException("无法启动插件工作进程"))
            }
            continuation.invokeOnCancellation { unbind() }
        }
        try { withContext(Dispatchers.IO) {
            worker.attachQuickTextBroker(quickText); worker.attachAppBroker(app); worker.attachStickerBroker(stickers)
            val snapshot = PluginJson.decodeFromString<PluginSnapshot>(worker.initialize(id, broker))
            worker.observeUi(object : IPluginUiObserver.Stub() { override fun changed(revision: Long) { uiChanged() } })
            snapshot
        } }
        catch (error: Exception) { stop(); throw error }
    }
    suspend fun stop() {
        (quickText as? HostQuickTextBroker)?.close()
        (app as? HostAppBroker)?.close()
        (stickers as? HostStickerBroker)?.close()
        if (::worker.isInitialized && !died.isCompleted) {
            runCatching { withContext(Dispatchers.IO) { worker.shutdown() } }
            withTimeout(10_000) { died.await() }
        }
        unbind()
        (broker as? HostSpeechBroker)?.close()
    }
    private fun unbind() {
        connection?.let { runCatching { context.unbindService(it) } }
        connection = null
    }
}
