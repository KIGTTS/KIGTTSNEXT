package com.kigtts.next.ui.plugins

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun InstalledPluginAbout(model: AppViewModel, id: String, dismiss: () -> Unit) {
    var failed by remember(id) { mutableStateOf(false) }
    var license by remember(id) { mutableStateOf(false) }
    val info by produceState<InstalledPluginDetails?>(null, id) {
        value = withContext(Dispatchers.IO) { runCatching { installedPluginDetails(model, id) }.getOrNull() }
        failed = value == null
    }
    MotionAlertDialog(dismiss, onNavigateUp = if (license) ({ license = false }) else null,
        text = {
            ModalPageTransition(license, { if (it) 1 else 0 }, Modifier.fillMaxWidth()) { showingLicense ->
              Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (showingLicense) uiText("plugin.license_title") else info?.name ?: uiText("plugin.about"),
                    Modifier.semantics { heading() }, color = AlertDialogDefaults.titleContentColor,
                    style = MaterialTheme.typography.headlineSmall)
                Column(Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                info?.let { detail ->
                    if (showingLicense) SelectionContainer { Text(detail.licenseText.ifBlank { detail.license.ifBlank { uiText("plugin.license_missing") } }) }
                    else {
                        Text(listOf("v${detail.version}", detail.author).filter(String::isNotBlank).joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (detail.description.isNotBlank()) ChatRichText(detail.description)
                        if (detail.homepage.isNotBlank()) SelectionContainer { Text(detail.homepage) }
                        if (detail.license.isNotBlank()) Text(uiText("plugin.license_label", "license" to detail.license))
                        if (detail.licenseText.isNotBlank()) TextButton(feedbackClick { license = true }) { Text(uiText("plugin.license_view")) }
                    }
                } ?: if (failed) Text(uiText("plugin.unavailable_details")) else CircularProgressIndicator()
                }
              }
            }
        }, confirmButton = {
            if (license) MotionTextButton(feedbackClick { license = false }) { Text(uiText("action.back")) }
            else MotionTextButton(dialogFeedbackClick(dismiss)) { Text(uiText("action.close")) }
        })
}
