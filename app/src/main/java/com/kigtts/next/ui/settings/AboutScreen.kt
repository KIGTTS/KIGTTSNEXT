package com.kigtts.next.ui.settings

import com.kigtts.next.i18n.rememberUiCopy

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.SettingsCategory

internal const val ABOUT_PROJECT_URL = "https://github.com/KIGTTS/KIGTTSNEXT"

@Composable
internal fun AboutScreen(openLink: (String) -> Unit, openDocument: (SettingsCategory) -> Unit,
    version: String? = null) {
    val copy = rememberUiCopy()
    val context = LocalContext.current
    val installedVersion = version ?: remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    SettingsLayout {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("about-brand")
            .settingFocus(copy("about.version_label")), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AboutBrand()
            Text(copy("about.tagline"), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(copy("about.version", "version" to installedVersion), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
        }
        SettingsGroup(copy("about.legal")) {
            SettingItem(copy("settings.category.user_agreement"), "", Icons.Default.Description, SettingPosition.FIRST,
                onClick = { openDocument(SettingsCategory.USER_AGREEMENT) })
            SettingItem(copy("settings.category.privacy_policy"), "", Icons.Default.PrivacyTip, SettingPosition.MIDDLE,
                onClick = { openDocument(SettingsCategory.PRIVACY_POLICY) })
            SettingItem(copy("about.licenses"), "", Icons.AutoMirrored.Filled.Article, SettingPosition.LAST,
                onClick = { openDocument(SettingsCategory.OPEN_SOURCE_LICENSES) })
        }
        SettingsGroup(copy("about.help_feedback")) {
            SettingItem(copy("about.project_home"), copy("about.project_hint"), Icons.Default.Code, SettingPosition.FIRST,
                onClick = { openLink(ABOUT_PROJECT_URL) }, trailing = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) })
            SettingItem(copy("about.feedback"), copy("about.feedback_hint"), Icons.Default.Feedback, SettingPosition.LAST,
                onClick = { openLink("$ABOUT_PROJECT_URL/issues") }, trailing = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) })
        }
    }
}
