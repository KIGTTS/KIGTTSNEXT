package com.kigtts.next.ui.plugins

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.runtime.PluginPermissions

/** A fixed identity/action area surrounds the internally scrolling review tabs. No grants here. */
@Composable
internal fun PackageInstallerDialog(packages: List<PackageInstallerInfo>?, busy: Boolean, completed: Boolean,
    error: String?, allowDowngrade: Boolean = false, installedNames: List<String> = emptyList(),
    install: () -> Unit, dismiss: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val labels = installerLabels(packages.orEmpty())
    MotionAlertDialog(onDismissRequest = dismiss, dismissEnabled = !busy,
        text = {
            Column(Modifier.fillMaxWidth().height(480.dp).testTag("package-installer"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                packages?.lastOrNull()?.let { info -> PackageInstallerIdentity(info) }
                if (completed) {
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(8.dp))
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(labels.result, style = MaterialTheme.typography.titleLarge)
                        Text(if (packages.orEmpty().any { it.previous != null }) "新增权限将在使用时申请" else "权限将在使用相关功能时申请", style = MaterialTheme.typography.bodyMedium)
                        installedNames.forEach { Text(it) }
                    }
                } else {
                    if (packages != null) {
                        ChoiceButtonGroup("安装详情", listOf(0, 1, 2), tab,
                            { listOf("介绍", "权限", "许可")[it] }, { tab = it }, compactLabels = true,
                            itemRole = androidx.compose.ui.semantics.Role.Tab)
                    }
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("installer-details"),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (packages == null && error == null) Text(uiText("plugin.inspecting"))
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (installedNames.isNotEmpty()) Text(uiText("plugin.installed_list", "names" to installedNames.joinToString()))
                        packages.orEmpty().forEach { info ->
                            if (packages.orEmpty().size > 1) Text(info.manifest.name, style = MaterialTheme.typography.titleMedium)
                            when (tab) {
                                0 -> PackageInstallerIntroduction(info) { tab = 1 }
                                1 -> PackageInstallerPermissions(info)
                                else -> PackageInstallerLicense(info)
                            }
                        }
                        if (allowDowngrade) Text(uiText("plugin.downgrade_hint"), color = MaterialTheme.colorScheme.error)
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            if (completed) MotionTextButton(onClick = dialogFeedbackClick(dismiss)) { Text(labels.done) }
            else Button(onClick = feedbackClick(install), enabled = packages != null && !busy) {
                Text(if (busy) labels.working else labels.action)
            }
        },
        dismissButton = { if (!completed) MotionTextButton(onClick = dialogFeedbackClick(dismiss), enabled = !busy) { Text(uiText("action.cancel")) } })
}

@Composable private fun PackageInstallerIdentity(info: PackageInstallerInfo) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (info.image != null) Image(info.image.asImageBitmap(), "${info.manifest.name}图标",
            Modifier.size(48.dp).testTag("installer-package-image"), contentScale = ContentScale.Fit)
        else Icon(MaterialSymbols.vector(info.symbol) ?: when {
            "speech-service" in info.manifest.capabilities -> Icons.Default.RecordVoiceOver
            "asr-service" in info.manifest.capabilities -> Icons.Default.GraphicEq
            else -> Icons.Default.Extension
        }, null, Modifier.size(48.dp).testTag("installer-package-symbol"), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(info.manifest.name, style = MaterialTheme.typography.titleLarge)
            Text(listOf(info.author, info.previous?.let { "v${it.version} → v${info.manifest.version}" } ?: "v${info.manifest.version}").filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            Text(uiText("plugin.package_size", "size" to android.text.format.Formatter.formatShortFileSize(androidx.compose.ui.platform.LocalContext.current, info.candidate.file.length())), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun PackageInstallerIntroduction(info: PackageInstallerInfo, permissions: () -> Unit) {
    Text(uiText("plugin.about_this"), style = MaterialTheme.typography.titleSmall)
    if (info.description.isNotBlank()) ChatRichText(info.description)
    else Text(uiText("plugin.install_use", "name" to info.manifest.name))
    HorizontalDivider()
    InstallerInfoRow("软件许可", info.manifest.license)
    InstallerInfoRow("兼容版本", "支持当前应用与设备")
    TextButton(onClick = feedbackClick(permissions), contentPadding = PaddingValues(0.dp)) { Text(uiText("plugin.permission_count", "count" to info.permissions.size.toString())) }
    Text(uiText("plugin.publisher_hash"), style = MaterialTheme.typography.labelMedium)
    SelectionContainer { Text(info.candidate.preview.publisherSha256, style = MaterialTheme.typography.bodySmall) }
}

@Composable private fun InstallerInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(1f)); Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable private fun PackageInstallerLicense(info: PackageInstallerInfo) {
    Text(uiText("plugin.license_title"), style = MaterialTheme.typography.titleSmall)
    Text(info.manifest.license, style = MaterialTheme.typography.titleMedium)
    SelectionContainer {
        Text(info.licenseText.ifBlank { "此插件使用 ${info.manifest.license} 许可。安装包未附许可全文，请向发布者获取全文和所用组件的许可。" })
    }
}
