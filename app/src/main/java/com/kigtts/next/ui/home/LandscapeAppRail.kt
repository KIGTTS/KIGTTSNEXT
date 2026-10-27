package com.kigtts.next.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kigtts.next.R
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.plugins.SlotActions
import com.kigtts.plugin.api.UiSlot

@Composable
fun LandscapeAppRail(state: AppState, model: AppViewModel, openSettings: () -> Unit) {
    val recovery by model.safeModeSession.state.collectAsStateWithLifecycle()
    Column(Modifier.width(64.dp).fillMaxHeight().testTag("landscape-app-rail"), horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(feedbackClick(openSettings), modifier = Modifier.testTag("open-settings").homePreviewRegion("header-end"),
            colors = compactPlainIconColors()) { Icon(Icons.Default.Settings, "打开设置") }
        SlotActions(UiSlot.HEADER_END, model.registry, model::invoke, Modifier.width(40.dp), context = model.context(), revision = state.pluginRevision)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            com.kigtts.next.ui.navigation.SafeModeBrand(recovery.active,
                Modifier.requiredSize(128.dp, if (recovery.active) 44.dp else 32.dp).homePreviewRegion("header-logo").graphicsLayer { rotationZ = -90f })
        }
        SlotActions(UiSlot.HEADER_START, model.registry, model::invoke, Modifier.width(40.dp), context = model.context(), revision = state.pluginRevision)
        AutomaticReadButton(state, model)
    }
}
