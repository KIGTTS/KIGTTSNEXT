@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kigtts.manager

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

class ManagerActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState);enableEdgeToEdge()
        setContent {
            val context=LocalContext.current;val dark=isSystemInDarkTheme()
            val colors=if(Build.VERSION.SDK_INT>=31) { if(dark)dynamicDarkColorScheme(context) else dynamicLightColorScheme(context) }
                else if(dark)darkColorScheme(primary=Color(0xff4fd8df)) else lightColorScheme(primary=Color(0xff00696e))
            MaterialTheme(colorScheme=colors) { ManagerScreen(ManagerController.get(this)) }
        }
    }
}

@Composable
private fun ManagerScreen(controller:ManagerController) {
    val state by controller.state.collectAsStateWithLifecycle();val lifecycle=LocalLifecycleOwner.current.lifecycle
    val context=LocalContext.current;val prefs=remember { context.getSharedPreferences("monitor",0) }
    var monitoring by remember { mutableStateOf(prefs.getBoolean("enabled",false)) }
    DisposableEffect(prefs) {
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener { _,key ->
            if(key=="enabled")monitoring=prefs.getBoolean("enabled",false)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun monitor(enabled:Boolean) {
        monitoring=enabled;prefs.edit().putBoolean("enabled",enabled).apply()
        if(enabled) { if(Build.VERSION.SDK_INT>=33)permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            context.startForegroundService(Intent(context,MonitorService::class.java)) }
        else context.stopService(Intent(context,MonitorService::class.java))
    }
    LaunchedEffect(controller) { if(monitoring)context.startForegroundService(Intent(context,MonitorService::class.java))
        while(true) { if(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))controller.refresh();delay(3000) } }
    Scaffold(topBar={ TopAppBar(title={ Text("KIGTTS 插件管理器") }) }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).padding(horizontal=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("持续监控",Modifier.padding(top=12.dp));Switch(monitoring,::monitor)
            }
            state.snapshot?.let { Text("插件 API ${it.optInt("pluginApi")} · 主进程 ${it.optInt("pid")}",style=MaterialTheme.typography.labelMedium) }
            (state.error ?: state.connectionError)?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            ManagerContent(controller,state,Modifier.weight(1f))
        }
    }
}
