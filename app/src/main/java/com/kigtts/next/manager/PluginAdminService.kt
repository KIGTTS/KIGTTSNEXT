package com.kigtts.next.manager

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import com.kigtts.manager.protocol.*
import com.kigtts.plugin.api.PLUGIN_API_VERSION
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.util.UUID

class PluginAdminService : Service() {
    private lateinit var host:HostPluginRuntime
    private val journal=AdminJournal()
    private lateinit var commands:AdminCommands
    private var initialized=false
    private val preparation=kotlinx.coroutines.sync.Mutex()
    override fun onCreate() { super.onCreate();host=HostPluginRuntime.get(application);commands=AdminCommands(this,host,journal) }
    private fun authorize() {
        val uid=Binder.getCallingUid()
        check(AdminContract.authorized(uid,Process.myUid(),packageManager.checkSignatures(uid,Process.myUid())==PackageManager.SIGNATURE_MATCH,
            packageManager.getPackagesForUid(uid)?.toList().orEmpty())) { "管理器未授权" }
    }
    private val binder=object:IPluginAdmin.Stub() {
        override fun describe():String { authorize();return JSONObject().put("bridgeVersion",AdminContract.VERSION).put("pluginApi",PLUGIN_API_VERSION).toString() }
        override fun call(command:String,payload:String,descriptor:ParcelFileDescriptor?):String {
            authorize();require(payload.toByteArray().size<=AdminContract.MAX_JSON&&command.length<=64)
            return runBlocking(Dispatchers.IO) {
                var stage:File?=null
                try {
                    preparation.lock()
                    try { if(!initialized) { host.manager.reload();initialized=true } } finally { preparation.unlock() }
                    val args=JSONObject(payload)
                    descriptor?.let { fd ->
                        stage=File(cacheDir,"admin-${UUID.randomUUID()}.incoming")
                        ParcelFileDescriptor.AutoCloseInputStream(fd).use { input -> stage!!.outputStream().use { output ->
                            val buffer=ByteArray(64*1024);var bytes=0L
                            while(true) { val count=input.read(buffer);if(count<0)break;bytes+=count
                                require(bytes<=if(command=="resource")2L*1024*1024*1024 else 768L*1024*1024) { "传入文件过大" };output.write(buffer,0,count) }
                        } }
                    }
                    val result=commands.run(command,args,stage)
                    if(command !in setOf("snapshot","inspect"))journal.record(command,args.optString("id"),true,"完成")
                    JSONObject().put("ok",true).put("data",result).toString()
                } catch(error:Exception) {
                    journal.record(command,"",false,error.message ?: "操作失败")
                    JSONObject().put("ok",false).put("error",error.message ?: "操作失败").toString()
                } finally { stage?.delete();descriptor?.close() }
            }
        }
    }
    override fun onBind(intent:Intent)=binder
}
