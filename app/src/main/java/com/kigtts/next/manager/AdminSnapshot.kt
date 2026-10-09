package com.kigtts.next.manager

import android.app.ActivityManager
import android.content.Context
import com.kigtts.manager.protocol.AdminContract
import com.kigtts.plugin.api.PLUGIN_API_VERSION
import org.json.*

suspend fun adminSnapshot(context: Context, host: HostPluginRuntime, journal: AdminJournal): JSONObject {
    val manager=host.manager;val workers=manager.workers().associateBy { it.id }
    val processes=(context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).runningAppProcesses.orEmpty()
    val plugins=JSONArray()
    manager.status.forEach { status ->
        val installed=status.installed;val manifest=installed.manifest;val worker=workers[manifest.id]
        val process=worker?.let { processes.firstOrNull { process -> process.processName==context.packageName+":plugin"+it.slot } }
        plugins.put(JSONObject().put("id",manifest.id).put("name",manifest.name).put("version",manifest.version)
            .put("api",manifest.apiVersion).put("enabled",installed.enabled).put("loaded",worker?.alive==true)
            .put("builtin",false).put("pid",process?.pid ?: 0).put("error",status.error ?: if(worker?.alive==false)"工作进程已退出" else "")
            .put("publisher",installed.publisherSha256).put("capabilities",JSONArray(manifest.capabilities)))
    }
    manager.registry.plugins.filter { plugin -> manager.status.none { it.installed.manifest.id==plugin.descriptor.id } }.forEach { plugin ->
        plugins.put(JSONObject().put("id",plugin.descriptor.id).put("name",plugin.descriptor.name).put("version",plugin.descriptor.version)
            .put("api",plugin.descriptor.apiVersion).put("builtin",true).put("enabled",true).put("loaded",true).put("pid",android.os.Process.myPid())
            .put("error",if(manager.registry.available(plugin))"" else "需要语音识别服务"))
    }
    return JSONObject().put("bridgeVersion",AdminContract.VERSION).put("pluginApi",PLUGIN_API_VERSION)
        .put("pid",android.os.Process.myPid()).put("time",System.currentTimeMillis()).put("plugins",plugins)
        .put("listening",host.state()?.listening==true).put("speaking",host.state()?.speaking==true).put("logs",journal.snapshot())
        .put("mediaId",host.state()?.mediaId).put("uiAttached",host.ui()!=null)
}
