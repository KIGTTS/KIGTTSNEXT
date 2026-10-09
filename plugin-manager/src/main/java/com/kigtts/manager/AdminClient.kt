package com.kigtts.manager

import android.content.*
import android.os.*
import com.kigtts.manager.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AdminClient(private val context:Context) {
    private val lock=Mutex();@Volatile private var api:IPluginAdmin?=null;@Volatile private var connection:ServiceConnection?=null
    private suspend fun connect():IPluginAdmin=lock.withLock {
        api?.takeIf { it.asBinder().isBinderAlive }?.let { return@withLock it }
        connection?.let { runCatching { context.unbindService(it) } };connection=null;api=null
        withTimeout(10_000) { suspendCancellableCoroutine { result ->
            val binding=object:ServiceConnection {
                override fun onServiceConnected(name:ComponentName,binder:IBinder) {
                    val candidate=IPluginAdmin.Stub.asInterface(binder)
                    if(result.isActive) { api=candidate;result.resume(candidate) }
                }
                override fun onServiceDisconnected(name:ComponentName) { api=null }
                override fun onNullBinding(name:ComponentName) { if(result.isActive)result.resumeWithException(IllegalStateException("主程序没有管理接口")) }
                override fun onBindingDied(name:ComponentName) { api=null;if(result.isActive)result.resumeWithException(IllegalStateException("主程序连接已退出")) }
            }
            connection=binding
            result.invokeOnCancellation { runCatching { context.unbindService(binding) };if(connection===binding)connection=null }
            try { if(!context.bindService(Intent().setComponent(ComponentName(AdminContract.HOST,AdminContract.SERVICE)),binding,Context.BIND_AUTO_CREATE))
                result.resumeWithException(IllegalStateException("无法连接主程序，请先打开 KIGTTS，或允许它在后台启动")) }
            catch(error:Exception) { result.resumeWithException(error) }
        } }
    }
    suspend fun call(command:String,payload:JSONObject=JSONObject(),file:File?=null):JSONObject = withContext(Dispatchers.IO) {
        val remote=connect();val hello=JSONObject(remote.describe());check(hello.getInt("bridgeVersion")==AdminContract.VERSION) { "管理协议不兼容" }
        val response=if(file==null)remote.call(command,payload.toString(),null) else ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use { remote.call(command,payload.toString(),it) }
        JSONObject(response).also { check(it.optBoolean("ok")) { it.optString("error","操作失败") } }.getJSONObject("data")
    }
    fun close() { connection?.let { runCatching { context.unbindService(it) } };connection=null;api=null }
}
