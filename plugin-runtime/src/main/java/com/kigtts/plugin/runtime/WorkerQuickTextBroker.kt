package com.kigtts.plugin.runtime

import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.UUID

internal class WorkerQuickTextBroker(private val remote:IHostQuickTextBroker):QuickTextBroker {
    override suspend fun request(command:QuickTextCommand):QuickTextReply=withContext(Dispatchers.IO) {
        val json=PluginJson.encodeToString(command)
        if(json.toByteArray().size>HostQuickTextBroker.MAX_REQUEST)return@withContext QuickTextReply.Failure(QuickTextError.TOO_LARGE,"请缩小批次或使用资源文件")
        PluginJson.decodeFromString(remote.request(json))
    }
    override fun changes(afterRevision:Long):Flow<QuickTextChange> = callbackFlow {
        val token=UUID.randomUUID().toString()
        val observer=object:IQuickTextObserver.Stub() {override fun changed(value:String) {runCatching {
            if(value.toByteArray().size<=8192)trySend(PluginJson.decodeFromString<QuickTextChange>(value))
        }} }
        val death=android.os.IBinder.DeathRecipient {close(IllegalStateException("插件连接已断开，请重新读取快捷文本"))}
        remote.asBinder().linkToDeath(death,0)
        try {remote.subscribe(token,afterRevision,observer)} catch(e:Exception) {runCatching {remote.asBinder().unlinkToDeath(death,0)};throw e}
        awaitClose {runCatching {remote.unsubscribe(token)};runCatching {remote.asBinder().unlinkToDeath(death,0)}}
    }.buffer(Channel.CONFLATED).flowOn(Dispatchers.IO)
}
