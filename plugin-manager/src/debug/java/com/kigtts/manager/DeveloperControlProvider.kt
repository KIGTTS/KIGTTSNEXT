package com.kigtts.manager

import android.content.*
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import org.json.JSONObject

/** Debug-only shell entrypoint. Android's DUMP permission excludes ordinary third-party apps. */
class DeveloperControlProvider:ContentProvider() {
    override fun onCreate()=true
    override fun call(method:String,arg:String?,extras:Bundle?):Bundle {
        val controller=ManagerController.get(requireNotNull(context))
        val result=when(method) {
            "enqueue" -> { val data=JSONObject(String(android.util.Base64.decode(requireNotNull(arg),android.util.Base64.DEFAULT),Charsets.UTF_8));
                JSONObject().put("job",controller.submit(data.getString("command"),data.optJSONObject("payload") ?: JSONObject(),data.optString("file").takeIf(String::isNotBlank))).toString() }
            "job" -> controller.job(requireNotNull(arg))
            else -> error("未知控制方法")
        }
        return Bundle().apply { putString("result",result) }
    }
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor?=null
    override fun getType(uri:Uri):String?=null
    override fun insert(uri:Uri,values:ContentValues?):Uri?=null
    override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?)=0
    override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?)=0
}
