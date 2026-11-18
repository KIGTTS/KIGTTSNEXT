package com.kigtts.next.ui.navigation

import com.kigtts.next.i18n.uiText
import com.kigtts.next.i18n.rememberUiCopy

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
    val copy = rememberUiCopy()
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
            HomeExitAction.HINT -> hint(copy("feedback.exit_again"))
            HomeExitAction.LEAVE -> {
                if (leave != null) leave()
                else generateSequence(context) { (it as? ContextWrapper)?.baseContext }
                    .filterIsInstance<Activity>().firstOrNull()?.moveTaskToBack(true)
            }
        }
    }
    if (asking) MotionAlertDialog(onDismissRequest = { asking = false }, title = { Text(uiText("feedback.exit_prompt")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(uiText("feedback.exit_question"))
            listOf(true to uiText("feedback.exit_twice"), false to uiText("feedback.exit_once")).forEach { (value, label) ->
                Row(Modifier.fillMaxWidth().selectable(selection == value, role = Role.RadioButton,
                    onClick = feedbackClick { selection = value }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selection == value, null); Spacer(Modifier.width(8.dp)); Text(label)
                }
            }
        } }, confirmButton = { MotionTextButton(dialogFeedbackClick { preference.choose(selection); asking = false }) { Text(uiText("common.confirm")) } },
        dismissButton = { MotionTextButton(dialogFeedbackClick { asking = false }) { Text(uiText("overlay.later")) } })
}
