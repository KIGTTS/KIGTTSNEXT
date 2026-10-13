@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.navigation.*
import kotlinx.coroutines.CancellationException

@Composable
internal fun BoxScope.CardAddFab(enabled: Boolean, camera: () -> Unit, gallery: () -> Unit, empty: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val active = LocalPageActive.current
    // Opening a menu performs no work. Busy imports/exports lock only its actions.
    LaunchedEffect(active) { if (!active) expanded = false }
    RegisterPageBackGuard(active && expanded) { expanded = false }
    PredictiveBackHandler(active && expanded) { events ->
        try { events.collect { }; expanded = false } catch (_: CancellationException) { }
    }
    if (active && expanded) Box(Modifier.matchParentSize().zIndex(2f).clickable(remember { MutableInteractionSource() }, indication = null,
        onClick = feedbackClick { expanded = false }).testTag("dismiss-card-fab"))
    val toggle = feedbackChange { expanded = it }
    val actionContainer by animateColorAsState(if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "card-fab-action-container")
    val actionContent by animateColorAsState(if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f),
        label = "card-fab-action-content")
    FloatingActionButtonMenu(expanded, button = {
        ToggleFloatingActionButton(expanded, { if (active) toggle(it) },
            Modifier.testTag("card-add-fab").semantics {
                contentDescription = if (expanded) "关闭新建拍立得菜单" else "新建拍立得"
                if (!active) disabled()
            }) {
            Icon(if (checkedProgress > .5f) Icons.Default.Close else Icons.Default.Add, null,
                Modifier.animateIcon({ checkedProgress }))
        }
    }, modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 12.dp).zIndex(3f)) {
        listOf(Triple("拍照", Icons.Default.CameraAlt, camera),
            Triple("选图", Icons.Default.PhotoLibrary, gallery),
            Triple("新建空白拍立得", Icons.AutoMirrored.Filled.NoteAdd, empty)).forEachIndexed { index, (label, icon, action) ->
            val click = feedbackClick { expanded = false; action() }
            FloatingActionButtonMenuItem({ if (active && enabled && expanded) click() }, { Text(label) }, { Icon(icon, null) },
                Modifier.testTag("card-add-action/$index").semantics { if (!enabled || !expanded || !active) disabled() },
                containerColor = actionContainer, contentColor = actionContent)
        }
    }
}
