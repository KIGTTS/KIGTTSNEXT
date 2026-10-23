package com.kigtts.next.ui.plugins

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.plugin.runtime.PluginPermissions

@Composable
internal fun PackageInstallerPermissions(info: PackageInstallerInfo) {
    val updating = info.previous != null
    Text(if (updating) "新增权限" else "插件访问权限", style = MaterialTheme.typography.titleSmall)
    Text("需要使用时再授权", style = MaterialTheme.typography.bodySmall)
    val visible = if (updating) info.addedPermissions else info.permissions
    if (visible.isEmpty()) Text(if (updating) "本次更新没有新增权限" else "此插件未声明额外权限")
    PermissionRows(info, visible, newPermissions = true)
    if (updating && info.existingPermissions.isNotEmpty()) {
        var expanded by remember(info.candidate) { mutableStateOf(false) }
        TextButton(onClick = feedbackClick { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
            Text("已有权限 · ${info.existingPermissions.size}")
            Spacer(Modifier.width(8.dp))
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "收起已有权限" else "展开已有权限")
        }
        AnimatedVisibility(expanded) { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PermissionRows(info, info.existingPermissions, newPermissions = false)
        } }
    }
}

@Composable
private fun PermissionRows(info: PackageInstallerInfo, permissions: List<String>, newPermissions: Boolean) {
    permissions.forEach { permission ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icon(permissionIcon(permission), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(PluginPermissions.label(permission), style = MaterialTheme.typography.titleSmall)
                info.manifest.permissionPurposes[permission]?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (newPermissions) Text("使用时询问", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
