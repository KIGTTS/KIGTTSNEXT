package com.kigtts.next.ui.plugins

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.management.ResourceAvatar
import com.kigtts.next.ui.management.InlineProgressButton
import com.kigtts.next.ui.navigation.LocalPageActive
import com.kigtts.plugin.api.*
import java.io.File

/** Entry effects wait while native reviews or other modal surfaces cover the page. */
internal val LocalPluginEntryBlocked = staticCompositionLocalOf { false }

/** A retained market route keeps its refresh state as users browse its children. */
@Composable internal fun PluginPageEntry(page: PluginPage?, busy: Boolean, ready: Boolean = true,
    retainEntry: Boolean = false, invoke: (String, Map<String, String>) -> Unit) {
    val active = LocalPageActive.current
    val blocked = LocalPluginEntryBlocked.current
    val action = page?.onEnterAction
    var entered by remember(page?.id, action) { mutableStateOf(false) }
    val currentInvoke by rememberUpdatedState(invoke)
    LaunchedEffect(page?.id, action, active, busy, blocked, ready, retainEntry) {
        if (!active) {
            if (!retainEntry) entered = false
        }
        else if (action != null && !busy && !blocked && ready && !entered) {
            entered = true
            currentInvoke(action, emptyMap())
        }
    }
}

@Composable internal fun PluginPageHeader(item: ManagementItem?, resolve: (PluginResource) -> File?,
    action: ManagementProgressAction? = null, enabled: Boolean = true,
    invoke: (String, Map<String, String>) -> Unit = { _, _ -> }, loadArtwork: Boolean = true) {
    if (item == null) return
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("plugin-page-header"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ResourceAvatar(if (loadArtwork) item else item.copy(image = null), resolve, 80.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.title, style = MaterialTheme.typography.headlineSmall)
            if (item.subtitle.isNotBlank()) Text(item.subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action?.let { InlineProgressButton(item.id, it, enabled, invoke) }
    }
}

@Composable internal fun PluginCollapsibleGroup(block: PageBlock.Group, content: @Composable () -> Unit) {
    var expanded by rememberSaveable(block.title) { mutableStateOf(block.initiallyExpanded) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "group-chevron")
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            Row(Modifier.fillMaxWidth().testTag("plugin-group/" + block.title)
                .semantics { stateDescription = if (expanded) "已展开" else "已折叠" }
                .clickable(role = Role.Button) { expanded = !expanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(block.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Icon(Icons.Default.ExpandMore, null, Modifier.size(24.dp).rotate(rotation))
            }
            AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) { content() }
            }
        }
    }
}
