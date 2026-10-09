package com.kigtts.manager

import android.content.Context
import android.net.Uri
import com.kigtts.manager.protocol.AdminContract
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class ManagerState(val snapshot:JSONObject?=null,val error:String?=null,val connectionError:String?=null,val busy:Boolean=false,val result:String="")
class ManagerController private constructor(val context:Context) {
    val client=AdminClient(context);val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val operations=Mutex();private val mutable=MutableStateFlow(ManagerState());val state=mutable.asStateFlow()
    val inbox=File(context.getExternalFilesDir(null) ?: context.filesDir,"appdata/Kigtts/plugin-manager/inbox").apply { mkdirs() }
    private val jobs=linkedMapOf<String,JSONObject>()
    fun report(error:Exception) { mutable.update { it.copy(error=error.message ?: "操作失败") } }
    suspend fun refresh() { if(!operations.tryLock())return;try { updateSnapshot() } finally { operations.unlock() } }
    private suspend fun updateSnapshot() { try { mutable.update { it.copy(snapshot=client.call("snapshot"),connectionError=null) } }
        catch(error:Exception) { mutable.update { it.copy(connectionError=error.message) } } }
    suspend fun operation(command:String,payload:JSONObject=JSONObject(),file:File?=null):JSONObject=operations.withLock {
        mutable.update { it.copy(busy=true,error=null) }
        try { client.call(command,payload,file).also { response -> mutable.update { it.copy(result=response.toString(2)) } } }
        catch(error:Exception) { mutable.update { it.copy(error=error.message) };throw error }
        finally { updateSnapshot();mutable.update { it.copy(busy=false) } }
    }
    suspend fun import(uri:Uri):File=withContext(Dispatchers.IO) {
        val file=File(inbox,"${UUID.randomUUID()}.kigplugin")
        try { context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { output ->
            val buffer=ByteArray(64*1024);var bytes=0L;while(true) { val count=input.read(buffer);if(count<0)break;bytes+=count;require(bytes<=768L*1024*1024);output.write(buffer,0,count) }
        } };file } catch(error:Exception) { file.delete();throw error }
    }
    @Synchronized fun job(id:String)=jobs[id]?.toString() ?: JSONObject().put("state","missing").toString()
    @Synchronized private fun putJob(id:String,value:JSONObject) { jobs[id]=value;while(jobs.size>50)jobs.remove(jobs.keys.first()) }
    fun submit(command:String,payload:JSONObject,fileName:String?):String {
        val id=UUID.randomUUID().toString();putJob(id,JSONObject().put("state","queued"))
        scope.launch { try {
            val file=fileName?.let { name -> require(name.matches(Regex("[a-zA-Z0-9._-]+")));File(inbox,name).canonicalFile.also { require(it.parentFile==inbox.canonicalFile&&it.isFile) } }
            putJob(id,JSONObject().put("state","running"));val response=operation(command,payload,file)
            putJob(id,JSONObject().put("state","done").put("ok",true).put("data",response))
        } catch(error:Exception) { putJob(id,JSONObject().put("state","done").put("ok",false).put("error",error.message)) } }
        return id
    }
    companion object {
        @Volatile private var instance:ManagerController?=null
        fun get(context:Context)=instance ?: synchronized(this) { instance ?: ManagerController(context.applicationContext).also { instance=it } }
    }
}
