package com.kigtts.next.ui.navigation

import android.app.Activity
import android.content.ContextWrapper
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*

/** Root-only back handling leaves the task in Recents and never terminates the process. */
@Composable internal fun HomeExitHandler(preference: HomeExitPreference, enabled: Boolean, hint: (String) -> Unit,
    leave: (() -> Unit)? = null, now: () -> Long = SystemClock::elapsedRealtime) {
    val choice by preference.doubleBack.collectAsStateWithLifecycle()
    val gate = remember { HomeExitGate() }
    var asking by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) gate.reset() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); gate.reset() }
    }
    LaunchedEffect(enabled, choice) { gate.reset() }
    BackHandler(enabled && !asking) {
        when (gate.back(choice, now())) {
            HomeExitAction.ASK -> { selection = true; asking = true }
            HomeExitAction.HINT -> hint("再按一次返回键退出")
            HomeExitAction.LEAVE -> {
                if (leave != null) leave()
                else generateSequence(context) { (it as? ContextWrapper)?.baseContext }
                    .filterIsInstance<Activity>().firstOrNull()?.moveTaskToBack(true)
            }
        }
    }
    if (asking) MotionAlertDialog(onDismissRequest = { asking = false }, title = { Text("选择返回习惯") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("在主界面按返回键时，你更习惯哪种方式？可在设置的“操作反馈”中更改。")
            listOf(true to "按两次返回键退出", false to "按一次返回键退出").forEach { (value, label) ->
                Row(Modifier.fillMaxWidth().selectable(selection == value, role = Role.RadioButton,
                    onClick = feedbackClick { selection = value }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selection == value, null); Spacer(Modifier.width(8.dp)); Text(label)
                }
            }
        } }, confirmButton = { MotionTextButton(dialogFeedbackClick { preference.choose(selection); asking = false }) { Text("确定") } },
        dismissButton = { MotionTextButton(dialogFeedbackClick { asking = false }) { Text("稍后再说") } })
}
