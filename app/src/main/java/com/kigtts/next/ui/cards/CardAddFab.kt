@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
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
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.navigation.*
import kotlinx.coroutines.CancellationException

@Composable
internal fun BoxScope.CardAddFab(enabled: Boolean, camera: () -> Unit, gallery: () -> Unit, empty: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) expanded = false }
    RegisterPageBackGuard(LocalPageActive.current && expanded) { expanded = false }
    PredictiveBackHandler(LocalPageActive.current && expanded) { events ->
        try { events.collect { }; expanded = false } catch (_: CancellationException) { }
    }
    if (expanded) Box(Modifier.matchParentSize().clickable(remember { MutableInteractionSource() }, indication = null,
        onClick = feedbackClick { expanded = false }).testTag("dismiss-card-fab"))
    val toggle = feedbackChange { expanded = it }
    FloatingActionButtonMenu(expanded, button = {
        ToggleFloatingActionButton(expanded, { if (enabled) toggle(it) },
            Modifier.testTag("card-add-fab").semantics {
                contentDescription = if (expanded) "关闭新建拍立得菜单" else "新建拍立得"
                if (!enabled) disabled()
            }) {
            Icon(if (checkedProgress > .5f) Icons.Default.Close else Icons.Default.Add, null,
                Modifier.animateIcon({ checkedProgress }))
        }
    }, modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 12.dp)) {
        listOf(Triple("拍照", Icons.Default.CameraAlt, camera),
            Triple("选图", Icons.Default.PhotoLibrary, gallery),
            Triple("新建空白拍立得", Icons.Default.NoteAdd, empty)).forEachIndexed { index, (label, icon, action) ->
            val click = feedbackClick { expanded = false; action() }
            FloatingActionButtonMenuItem({ if (enabled && expanded) click() }, { Text(label) }, { Icon(icon, null) },
                Modifier.testTag("card-add-action/$index").semantics { if (!enabled || !expanded) disabled() })
        }
    }
}
