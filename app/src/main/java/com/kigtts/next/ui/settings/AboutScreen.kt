package com.kigtts.next.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

internal const val ABOUT_PROJECT_URL = "https://github.com/KIGTTS/KIGTTSNEXT"

@Composable
internal fun AboutScreen(openLink: (String) -> Unit, version: String? = null) {
    val context = LocalContext.current
    val installedVersion = version ?: remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    SettingsLayout {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("about-brand")
            .settingFocus("版本"), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                AboutBrand(Modifier.requiredWidth(maxWidth + 32.dp))
            }
            Text("让想说的话，更好地被看见。", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("版本 $installedVersion", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
        }
        SettingsGroup("项目与反馈") {
            SettingItem("项目主页", "了解更新与使用方法", Icons.Default.Code, SettingPosition.FIRST,
                onClick = { openLink(ABOUT_PROJECT_URL) }, trailing = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) })
            SettingItem("反馈问题", "告诉我们哪里不顺手", Icons.Default.Feedback, SettingPosition.MIDDLE,
                onClick = { openLink("$ABOUT_PROJECT_URL/issues") }, trailing = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) })
            SettingItem("软件许可", "MIT License", Icons.Default.Description, SettingPosition.LAST,
                onClick = { openLink("$ABOUT_PROJECT_URL/blob/main/LICENSE") }, trailing = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) })
        }
        Text("LHTSTUDIO", Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
