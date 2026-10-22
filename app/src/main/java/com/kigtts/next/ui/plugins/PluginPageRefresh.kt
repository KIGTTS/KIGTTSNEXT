@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.plugins

import androidx.compose.foundation.layout.*
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import com.kigtts.next.ui.navigation.LocalPageActive
import com.kigtts.plugin.api.PluginPage

/** The pull indicator follows the finger; background work uses the shared toolbar ring. */
@Composable internal fun PluginPageRefresh(page: PluginPage, busy: Boolean,
    invoke: (String, Map<String, String>) -> Unit, content: @Composable () -> Unit) {
    val action = page.refreshAction
    if (action == null) { content(); return }
    val active = LocalPageActive.current
    val enabled = active && !busy && !page.loading
    val pull = rememberPullToRefreshState()
    val refresh = { if (enabled) invoke(action, emptyMap()) }
    PullToRefreshBox(isRefreshing = false, onRefresh = refresh, state = pull,
        modifier = Modifier.fillMaxSize().testTag("plugin-page-refresh").semantics {
            customActions = listOf(CustomAccessibilityAction("更新内容") {
                if (enabled) { refresh(); true } else false
            })
        }, indicator = {
            PullToRefreshDefaults.LoadingIndicator(state = pull, isRefreshing = false,
                modifier = Modifier.align(Alignment.TopCenter).testTag("plugin-page-pull-indicator"))
        }) { content() }
}
