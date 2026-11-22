package com.kigtts.next.ui.navigation

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.vector
import com.kigtts.next.R

@Composable
fun AppDrawer(state: AppState, model: AppViewModel, navigation: DrawerNavigation, contentDirection: LayoutDirection) {
    val back = rememberPredictiveReturn(navigation.active, complete = navigation::close)
    val close = navigation::close
    val landscape=LocalConfiguration.current.orientation==Configuration.ORIENTATION_LANDSCAPE
    // The stateful alpha overload closes inside its back callback, which can cancel mid-motion.
    ModalDrawerSheet(modifier = Modifier.widthIn(max = 320.dp).predictiveReturn(back, edgeOverride = if (landscape) 1 else 0).testTag("app-drawer")
        .onGloballyPositioned {
            val left = it.positionInRoot().x
            navigation.placed(left, left + it.size.width, it.findRootCoordinates().size.width.toFloat())
        }) {
        CompositionLocalProvider(LocalLayoutDirection provides contentDirection) {
            Column(Modifier.fillMaxHeight().padding(12.dp)) {
                Box(Modifier.fillMaxWidth().padding(start = 16.dp, top = 20.dp, bottom = 24.dp)) {
                    Icon(painterResource(R.drawable.logo_horizontal), "KIGTTS 抽屉 Logo",
                        Modifier.width(176.dp).height(44.dp).testTag("drawer-logo"), tint = MaterialTheme.colorScheme.onSurface)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    model.registry.drawerEntries().filterNot {
                        it.entry.settingsPage || it.plugin.descriptor.id == com.kigtts.next.state.NATIVE_BUILDER_ID
                    }.forEach { registered ->
                        val destination = Destination.PluginPage(registered.plugin.descriptor.id, registered.entry.pageId)
                        NavigationDrawerItem(label = { Text(registered.entry.label) }, selected = state.destination == destination,
                            icon = { Icon(registered.entry.icon.vector(), null) }, onClick = feedbackClick { model.navigate(destination); close() })
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                NavigationDrawerItem(label = { Text(uiText("overlay.category.settings")) }, modifier = Modifier.testTag("drawer-settings"),
                    selected = state.destination == Destination.Settings || state.destination is Destination.SettingsDetail || state.destination == Destination.Plugins,
                    icon = { Icon(Icons.Default.Settings, null) },
                    onClick = feedbackClick { model.navigate(Destination.Settings); close() })
            }
        }
    }
}
