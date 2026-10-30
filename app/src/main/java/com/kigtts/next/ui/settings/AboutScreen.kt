package com.kigtts.next.ui.settings

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
    val context = LocalContext.current
    val installedVersion = version ?: remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    SettingsLayout {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("about-brand")
            .settingFocus("版本"), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AboutBrand()
            Text("让想说的话，更好地被看见。", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("版本 $installedVersion", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
        }
        SettingsGroup("协议与隐私") {
            SettingItem("用户协议", "", Icons.Default.Description, SettingPosition.FIRST,
                onClick = { openDocument(SettingsCategory.USER_AGREEMENT) })
            SettingItem("隐私政策", "", Icons.Default.PrivacyTip, SettingPosition.MIDDLE,
                onClick = { openDocument(SettingsCategory.PRIVACY_POLICY) })
            SettingItem("开源许可证", "", Icons.AutoMirrored.Filled.Article, SettingPosition.LAST,
                onClick = { openDocument(SettingsCategory.OPEN_SOURCE_LICENSES) })
        }
        SettingsGroup("帮助与反馈") {
            SettingItem("项目主页", "了解更新与使用方法", Icons.Default.Code, SettingPosition.FIRST,
                onClick = { openLink(ABOUT_PROJECT_URL) }, trailing = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) })
            SettingItem("反馈问题", "告诉我们哪里不顺手", Icons.Default.Feedback, SettingPosition.LAST,
                onClick = { openLink("$ABOUT_PROJECT_URL/issues") }, trailing = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) })
        }
    }
}
