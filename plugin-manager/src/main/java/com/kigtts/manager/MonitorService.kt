package com.kigtts.manager

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import kotlinx.coroutines.*

class MonitorService:Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    override fun onCreate() {
        super.onCreate()
        val notifications=getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel("plugins","插件监控",NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(this,0,Intent(this,ManagerActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        val stop=PendingIntent.getService(this,1,Intent(this,MonitorService::class.java).setAction("stop"),PendingIntent.FLAG_IMMUTABLE)
        val notification=Notification.Builder(this,"plugins").setSmallIcon(android.R.drawable.ic_menu_manage).setContentTitle("KIGTTS 插件监控")
            .setContentText("持续检查插件状态").setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).addAction(android.R.drawable.ic_menu_close_clear_cancel,"停止",stop).build()
        if(Build.VERSION.SDK_INT>=34)startForeground(1,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(1,notification)
        scope.launch { while(isActive) { ManagerController.get(this@MonitorService).refresh();delay(3000) } }
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action=="stop") { getSharedPreferences("monitor",0).edit().putBoolean("enabled",false).apply();stopSelf() }
        return START_NOT_STICKY
    }
    override fun onDestroy() { scope.cancel();super.onDestroy() }
    override fun onBind(intent:Intent):IBinder?=null
}
