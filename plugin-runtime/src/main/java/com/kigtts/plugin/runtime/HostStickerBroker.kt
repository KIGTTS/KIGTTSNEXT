package com.kigtts.plugin.runtime

import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/** A connection-bound grant is revoked with its worker; no owner is accepted from JSON. */
internal class HostStickerBroker(private val backend:StickerBroker):IHostStickerBroker.Stub() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val subscriptions=ConcurrentHashMap<String,Job>()
    @Volatile private var active=true
    override fun request(command:String):String {
        if(!active)return encode(StickerReply.Failure(StickerError.PERMISSION_DENIED,"插件连接已关闭"))
        if(command.toByteArray().size>MAX_REQUEST)return encode(StickerReply.Failure(StickerError.TOO_LARGE,"请缩小批次，或通过插件资源文件传递内容"))
        return runBlocking(Dispatchers.IO) {
            try {encode(scope.async {backend.request(PluginJson.decodeFromString(command))}.await())}
            catch(e:Exception) {encode(StickerReply.Failure(StickerError.INVALID,e.message ?: "接口调用失败"))}
        }
    }
    private fun encode(reply:StickerReply):String=PluginJson.encodeToString(reply).let {value->
        if(value.toByteArray().size<=MAX_REPLY)value else PluginJson.encodeToString<StickerReply>(StickerReply.Failure(
            StickerError.TOO_LARGE,"请使用较小分页、按类型分页读取完整内容"))}
    override fun subscribe(token:String,afterRevision:Long,observer:IStickerObserver) {
        check(active);require(token.matches(Regex("[a-f0-9-]{36}"))&&subscriptions.size<8)
        unsubscribe(token)
        val job=scope.launch(start=CoroutineStart.LAZY) {try {backend.changes(afterRevision).collect {observer.changed(PluginJson.encodeToString(it))}}
            catch(cancel:CancellationException) {throw cancel} catch(_:Exception) { /* A dead observer must not terminate the host. */ }}
        val death=android.os.IBinder.DeathRecipient {unsubscribe(token)}
        observer.asBinder().linkToDeath(death,0)
        job.invokeOnCompletion {subscriptions.remove(token,job);runCatching {observer.asBinder().unlinkToDeath(death,0)}}
        subscriptions[token]=job;job.start()
    }
    override fun unsubscribe(token:String) {subscriptions.remove(token)?.cancel()}
    fun close() {active=false;scope.cancel();subscriptions.clear()}
    companion object {const val MAX_REQUEST=256*1024;const val MAX_REPLY=512*1024}
}
